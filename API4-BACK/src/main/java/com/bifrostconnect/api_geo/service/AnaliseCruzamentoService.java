package com.bifrostconnect.api_geo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.CalculosCargaResponse.TotalCategoria;
import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseRequest;
import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseResponse;
import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseResponse.PropriedadeAfetada;
import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseResponse.Sobreposicao;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;

// Tarefa 14: o Analista cruza as bases publicadas com as camadas de referência e recebe
// a lista de propriedades e hectares afetados. Lê o cache resultados_calculos (V20) em vez de
// refazer o ST_Intersection: a versão publicada é congelada, então os números têm que ser os
// mesmos que o Gestor aprovou, mesmo que a camada_referencia seja recarregada depois
@Service
public class AnaliseCruzamentoService {

    // "publicado" igual ao mapa: versao_fechada = TRUE, na cópia da vw_copia_vigente (V26)
    private static final String SQL_BASES_PUBLICADAS = """
            SELECT p.id AS processo_id, cv.arquivo_processado_id, p.versao_oficial,
                   c.nome AS conjunto, o.sigla AS orgao, p.ano_safra
              FROM processo p
              JOIN vw_copia_vigente cv ON cv.processo_id = p.id
              JOIN conjunto c ON c.id = p.conjunto_id
              JOIN orgao o ON o.id = p.orgao_id
             WHERE p.versao_fechada
               %s
             ORDER BY p.id
            """;

    // área do imóvel com a mesma conta da Tarefa 11 (geography pra sair em m²)
    private static final String SQL_SOBREPOSICOES = """
            SELECT rc.arquivo_processado_id, rc.codigo_imovel, rc.fonte_camada,
                   rc.codigo_alerta_app, rc.hectares_sobrepostos,
                   ROUND((ST_Area(fc.geom::geography) / 10000)::numeric, 4) AS area_hectares
              FROM resultados_calculos rc
              LEFT JOIN feicao_carga fc
                ON fc.arquivo_processado_id = rc.arquivo_processado_id
               AND fc.codigo_imovel = rc.codigo_imovel
             WHERE rc.arquivo_processado_id IN (%s)
               AND rc.fonte_camada IN (%s)
             ORDER BY rc.arquivo_processado_id, rc.codigo_imovel, rc.fonte_camada, rc.codigo_alerta_app
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CalculoAnaliticoService calculoAnaliticoService;

    public AnaliseCruzamentoService(JdbcTemplate jdbcTemplate, CalculoAnaliticoService calculoAnaliticoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.calculoAnaliticoService = calculoAnaliticoService;
    }

    private record BasePublicada(
            Long processoId, Long arquivoProcessadoId, String versaoOficial,
            String conjunto, String orgao, String anoSafra) {}

    private record LinhaSobreposicao(
            Long arquivoProcessadoId, String codigoImovel, BigDecimal areaHectares, Sobreposicao sobreposicao) {}

    @Transactional(readOnly = true)
    public CruzamentoAnaliseResponse cruzar(CruzamentoAnaliseRequest filtro) {
        List<BasePublicada> bases = buscarBases(filtro);
        conferirProcessosPedidos(filtro.processoIds(), bases);

        List<String> fontes = filtro.fontes().isEmpty()
                ? calculoAnaliticoService.fontesDasCategorias(filtro.categorias())
                : filtro.fontes();

        List<LinhaSobreposicao> linhas = bases.isEmpty() || fontes.isEmpty() ? List.of()
                : buscarSobreposicoes(bases, fontes);

        return montar(bases, fontes, linhas);
    }

    private List<BasePublicada> buscarBases(CruzamentoAnaliseRequest filtro) {
        StringBuilder condicoes = new StringBuilder();
        List<Object> argumentos = new ArrayList<>();

        if (!filtro.processoIds().isEmpty()) {
            condicoes.append(" AND p.id IN (").append(placeholders(filtro.processoIds().size())).append(")");
            argumentos.addAll(filtro.processoIds());
        }
        if (filtro.conjuntoId() != null) {
            condicoes.append(" AND p.conjunto_id = ?");
            argumentos.add(filtro.conjuntoId());
        }
        if (filtro.anoSafra() != null && !filtro.anoSafra().isBlank()) {
            condicoes.append(" AND p.ano_safra = ?");
            argumentos.add(filtro.anoSafra().trim());
        }
        if (filtro.orgaoId() != null) {
            condicoes.append(" AND p.orgao_id = ?");
            argumentos.add(filtro.orgaoId());
        }

        return jdbcTemplate.query(SQL_BASES_PUBLICADAS.formatted(condicoes), (rs, i) -> new BasePublicada(
                rs.getLong("processo_id"),
                rs.getLong("arquivo_processado_id"),
                rs.getString("versao_oficial"),
                rs.getString("conjunto"),
                rs.getString("orgao"),
                rs.getString("ano_safra")), argumentos.toArray());
    }

    // se o Analista pediu uma carga específica e ela não está publicada, avisa em vez de
    // devolver lista vazia (pareceria que a carga não tem nenhuma propriedade afetada)
    private void conferirProcessosPedidos(List<Long> pedidos, List<BasePublicada> bases) {
        List<Long> encontrados = bases.stream().map(BasePublicada::processoId).toList();
        List<Long> faltando = pedidos.stream().distinct().filter(id -> !encontrados.contains(id)).toList();
        if (!faltando.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Carga(s) " + faltando + " não estão publicadas (ou não batem com os filtros informados).");
        }
    }

    private List<LinhaSobreposicao> buscarSobreposicoes(List<BasePublicada> bases, List<String> fontes) {
        List<Object> argumentos = new ArrayList<>();
        bases.forEach(b -> argumentos.add(b.arquivoProcessadoId()));
        argumentos.addAll(fontes);

        String sql = SQL_SOBREPOSICOES.formatted(placeholders(bases.size()), placeholders(fontes.size()));

        // Tarefa 15: o ST_Area do imóvel roda aqui; geometria que quebra a conta vira 422
        return ExecucaoGeoespacial.executar(jdbcTemplate, "consulta de sobreposições das bases publicadas",
                () -> jdbcTemplate.query(sql, (rs, i) -> new LinhaSobreposicao(
                        rs.getLong("arquivo_processado_id"),
                        rs.getString("codigo_imovel"),
                        rs.getBigDecimal("area_hectares"),
                        new Sobreposicao(
                                calculoAnaliticoService.categoriaDaFonte(rs.getString("fonte_camada")),
                                rs.getString("fonte_camada"),
                                rs.getString("codigo_alerta_app"),
                                rs.getBigDecimal("hectares_sobrepostos"))), argumentos.toArray()));
    }

    private CruzamentoAnaliseResponse montar(
            List<BasePublicada> bases, List<String> fontes, List<LinhaSobreposicao> linhas) {

        Map<Long, BasePublicada> basePorArquivo = new LinkedHashMap<>();
        bases.forEach(b -> basePorArquivo.put(b.arquivoProcessadoId(), b));

        // agrupa por carga + imóvel mantendo a ordem do SQL
        Map<String, List<LinhaSobreposicao>> porImovel = new LinkedHashMap<>();
        for (LinhaSobreposicao linha : linhas) {
            porImovel.computeIfAbsent(linha.arquivoProcessadoId() + "|" + linha.codigoImovel(),
                    k -> new ArrayList<>()).add(linha);
        }

        List<PropriedadeAfetada> propriedades = new ArrayList<>(porImovel.size());
        for (List<LinhaSobreposicao> grupo : porImovel.values()) {
            LinhaSobreposicao primeira = grupo.get(0);
            BasePublicada base = basePorArquivo.get(primeira.arquivoProcessadoId());
            List<Sobreposicao> sobreposicoes = grupo.stream().map(LinhaSobreposicao::sobreposicao).toList();

            propriedades.add(new PropriedadeAfetada(
                    base.processoId(),
                    base.versaoOficial(),
                    base.conjunto(),
                    base.orgao(),
                    base.anoSafra(),
                    primeira.codigoImovel(),
                    primeira.areaHectares(),
                    somar(sobreposicoes),
                    sobreposicoes));
        }

        // toda categoria aparece, mesmo zerada, igual na Tarefa 13
        Map<Categoria, TotalCategoria> totais = new EnumMap<>(Categoria.class);
        for (Categoria categoria : Categoria.values()) {
            List<Sobreposicao> daCategoria = linhas.stream()
                    .map(LinhaSobreposicao::sobreposicao)
                    .filter(s -> s.categoria() == categoria)
                    .toList();
            totais.put(categoria, new TotalCategoria(daCategoria.size(), somar(daCategoria)));
        }

        return new CruzamentoAnaliseResponse(
                bases.size(),
                fontes,
                propriedades.size(),
                propriedades.stream().map(PropriedadeAfetada::hectaresAfetados).reduce(BigDecimal.ZERO, BigDecimal::add),
                totais,
                propriedades);
    }

    private static BigDecimal somar(List<Sobreposicao> sobreposicoes) {
        return sobreposicoes.stream()
                .map(Sobreposicao::hectaresSobrepostos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static String placeholders(int quantidade) {
        return String.join(", ", Collections.nCopies(quantidade, "?"));
    }
}
