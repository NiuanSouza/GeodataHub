package com.bifrostconnect.api_geo.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.CalculosCargaResponse;
import com.bifrostconnect.api_geo.dto.CalculosCargaResponse.AreaPoligono;
import com.bifrostconnect.api_geo.dto.CalculosCargaResponse.Intersecao;
import com.bifrostconnect.api_geo.dto.CalculosCargaResponse.TotalCategoria;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Cruzamento;
import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.entity.Processo;

// para cada polígono válido calcula a área em hectares e cruza
// com as camadas de APP, Reserva Legal e Alertas da camada_referencia
@Service
public class CalculoAnaliticoService {

    // área de cada polígono válido. A geography é pra área sair em m² de verdade (a geometria
    // está em graus, SIRGAS 2000). Não grava de volta na feicao_carga de propósito: UPDATE lá
    // dispara o trigger da V22 (FOR SHARE no processo) e trava o atualizarEstado, que roda em
    // REQUIRES_NEW. Como a conta é determinística, a consulta da Tarefa 13 refaz com o mesmo SQL
    private static final String SQL_AREA = """
            SELECT codigo_imovel, ROUND((ST_Area(geom::geography) / 10000)::numeric, 4) AS hectares
              FROM feicao_carga
             WHERE arquivo_processado_id = ?
               AND ST_IsValid(geom)
             ORDER BY codigo_imovel
            """;

    private static final String SQL_CRUZAMENTOS = """
            INSERT INTO resultados_calculos
                   (processo_id, arquivo_processado_id, codigo_imovel, fonte_camada, codigo_alerta_app, hectares_sobrepostos)
            SELECT ?, ?, s.codigo_imovel, s.fonte, s.codigo_alerta, s.hectares
              FROM (SELECT fc.codigo_imovel, cr.fonte,
                           COALESCE(cr.identificador_origem, 'ID_' || cr.id) AS codigo_alerta,
                           ROUND((ST_Area(ST_Union(ST_CollectionExtract(ST_Intersection(fc.geom,
                                   CASE WHEN ST_IsValid(cr.geom) THEN cr.geom
                                        ELSE ST_CollectionExtract(ST_MakeValid(cr.geom), 3) END), 3))::geography)
                                  / 10000)::numeric, 4) AS hectares
                      FROM feicao_carga fc
                      JOIN camada_referencia cr
                        ON cr.geom && fc.geom
                       AND cr.fonte IN (%s)
                     WHERE fc.arquivo_processado_id = ?
                       AND ST_IsValid(fc.geom)
                       AND ST_Intersects(fc.geom, cr.geom)
                     GROUP BY fc.codigo_imovel, cr.fonte, COALESCE(cr.identificador_origem, 'ID_' || cr.id)) s
             WHERE s.hectares >= ?
            RETURNING codigo_imovel, fonte_camada, codigo_alerta_app, hectares_sobrepostos
            """;

    // ---- Tarefa 13: leitura do que o Tratamento já gravou ----

    private static final String SQL_PROCESSO = """
            SELECT p.id, e.nome AS etapa, s.nome AS situacao
              FROM processo p
              LEFT JOIN etapa e ON e.id = p.etapa_atual_id
              LEFT JOIN situacao s ON s.id = p.situacao_atual_id
             WHERE p.id = ?
            """;

    // a carga pode ter mais de uma cópia; qual vale é decidido pela vw_copia_vigente (V26),
    // a mesma regra do mapa e da Tarefa 14
    private static final String SQL_ULTIMA_COPIA = """
            SELECT cv.arquivo_processado_id AS id
              FROM vw_copia_vigente cv
             WHERE cv.processo_id = ?
            """;

    private static final String SQL_CRUZAMENTOS_GRAVADOS = """
            SELECT codigo_imovel, fonte_camada, codigo_alerta_app, hectares_sobrepostos
              FROM resultados_calculos
             WHERE arquivo_processado_id = ?
             ORDER BY codigo_imovel, fonte_camada, codigo_alerta_app
            """;

    private final JdbcTemplate jdbcTemplate;

    // fonte da camada_referencia -> categoria do cálculo
    private final Map<String, Categoria> categoriaPorFonte = new LinkedHashMap<>();

    public CalculoAnaliticoService(
            JdbcTemplate jdbcTemplate,
            @Value("${calculo-analitico.fontes.app:}") List<String> fontesApp,
            @Value("${calculo-analitico.fontes.reserva-legal:}") List<String> fontesReservaLegal,
            @Value("${calculo-analitico.fontes.alertas:}") List<String> fontesAlertas) {
        this.jdbcTemplate = jdbcTemplate;
        mapear(fontesApp, Categoria.APP);
        mapear(fontesReservaLegal, Categoria.RESERVA_LEGAL);
        mapear(fontesAlertas, Categoria.ALERTA);
    }

    private void mapear(List<String> fontes, Categoria categoria) {
        for (String fonte : fontes) {
            String limpa = fonte.trim();
            if (!limpa.isEmpty()) {
                categoriaPorFonte.putIfAbsent(limpa, categoria);
            }
        }
    }

    // a categoria pode vir null se a fonte saiu do application.properties depois do cálculo
    public Categoria categoriaDaFonte(String fonte) {
        return categoriaPorFonte.get(fonte);
    }

    // Tarefa 14: fontes configuradas das categorias pedidas (vazio = todas)
    public List<String> fontesDasCategorias(Collection<Categoria> categorias) {
        return categoriaPorFonte.entrySet().stream()
                .filter(e -> categorias.isEmpty() || categorias.contains(e.getValue()))
                .map(Map.Entry::getKey)
                .toList();
    }

    private record Area(String codigoImovel, BigDecimal hectares) {}

    // savepoint: se o PostGIS estourar no meio, desfaz só o que o cálculo gravou
    // e a transação do Tratamento continua de pé pra mandar a carga pra quarentena (Tarefa 12).
    // Desde a Tarefa 15 o savepoint e o try/catch ficam no ExecucaoGeoespacial, que também
    // transforma o erro do PostGIS (divisão por zero, TopologyException...) em 422
    public ResultadoCalculoAnalitico calcular(Processo processo, ArquivoProcessado copiaTrabalho) {
        return ExecucaoGeoespacial.executar(jdbcTemplate, "cálculo de área e cruzamentos espaciais",
                () -> calcularNoSavepoint(processo, copiaTrabalho));
    }

    private ResultadoCalculoAnalitico calcularNoSavepoint(Processo processo, ArquivoProcessado copiaTrabalho) {
        Long arquivoId = copiaTrabalho.getId();

        List<Area> areas = jdbcTemplate.query(SQL_AREA,
                (rs, i) -> new Area(rs.getString("codigo_imovel"), rs.getBigDecimal("hectares")), arquivoId);
        BigDecimal areaTotal = areas.stream()
                .map(Area::hectares)
                .filter(h -> h != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Cruzamento> cruzamentos = cruzar(processo.getId(), arquivoId);

        return new ResultadoCalculoAnalitico(areas.size(), areaTotal, cruzamentos, conferir(areas, cruzamentos));
    }

    // confere se as contas fecham: polígono válido tem que ter área > 0 e a parte dele que cai
    // numa camada não pode ser maior que ele mesmo (a tolerância é só pro arredondamento)
    private List<String> conferir(List<Area> areas, List<Cruzamento> cruzamentos) {
        List<String> inconsistencias = new ArrayList<>();
        Map<String, BigDecimal> areaPorImovel = new HashMap<>();

        for (Area area : areas) {
            if (area.hectares() == null || area.hectares().signum() <= 0) {
                inconsistencias.add("Polígono " + area.codigoImovel() + " ficou com área "
                        + (area.hectares() == null ? "nula" : area.hectares().toPlainString() + " ha") + ".");
            } else {
                areaPorImovel.merge(area.codigoImovel(), area.hectares(), BigDecimal::add);
            }
        }

        for (Cruzamento cruzamento : cruzamentos) {
            BigDecimal areaImovel = areaPorImovel.get(cruzamento.codigoImovel());
            if (areaImovel != null && cruzamento.hectaresSobrepostos().compareTo(
                    areaImovel.add(ValidacaoTopologicaService.TOLERANCIA_SOBREPOSICAO_HA)) > 0) {
                inconsistencias.add("Cruzamento do polígono " + cruzamento.codigoImovel() + " com "
                        + cruzamento.fonteCamada() + " (" + cruzamento.codigoAlerta() + ") deu "
                        + cruzamento.hectaresSobrepostos().toPlainString() + " ha, maior que a área do imóvel ("
                        + areaImovel.toPlainString() + " ha).");
            }
        }
        return inconsistencias;
    }

    private List<Cruzamento> cruzar(Long processoId, Long arquivoId) {
        // sem nenhuma fonte configurada não tem com o que cruzar
        if (categoriaPorFonte.isEmpty()) {
            return Collections.emptyList();
        }

        List<Object> argumentos = new ArrayList<>();
        argumentos.add(processoId);
        argumentos.add(arquivoId);
        argumentos.addAll(categoriaPorFonte.keySet());
        argumentos.add(arquivoId);
        argumentos.add(ValidacaoTopologicaService.TOLERANCIA_SOBREPOSICAO_HA);

        String placeholders = String.join(", ", Collections.nCopies(categoriaPorFonte.size(), "?"));

        return jdbcTemplate.query(SQL_CRUZAMENTOS.formatted(placeholders), (rs, i) -> new Cruzamento(
                rs.getString("codigo_imovel"),
                categoriaPorFonte.get(rs.getString("fonte_camada")),
                rs.getString("fonte_camada"),
                rs.getString("codigo_alerta_app"),
                rs.getBigDecimal("hectares_sobrepostos")), argumentos.toArray());
    }

    private record SituacaoProcesso(String etapa, String situacao) {}

    // Tarefa 13: monta as métricas do Tratamento pra tela. Carga que ainda não chegou no
    // cálculo (ou parou na quarentena da topologia) volta com as listas vazias, não 404
    @Transactional(readOnly = true)
    public CalculosCargaResponse consultar(Long processoId) {
        List<SituacaoProcesso> processo = jdbcTemplate.query(SQL_PROCESSO,
                (rs, i) -> new SituacaoProcesso(rs.getString("etapa"), rs.getString("situacao")), processoId);
        if (processo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carga não encontrada para o ID: " + processoId);
        }

        List<Long> copias = jdbcTemplate.query(SQL_ULTIMA_COPIA, (rs, i) -> rs.getLong("id"), processoId);
        Long arquivoId = copias.isEmpty() ? null : copias.get(0);

        // Tarefa 15: o ST_Area é refeito na consulta, então também pode estourar aqui
        List<AreaPoligono> poligonos = arquivoId == null ? List.of()
                : ExecucaoGeoespacial.executar(jdbcTemplate, "consulta das áreas dos polígonos",
                        () -> jdbcTemplate.query(SQL_AREA, (rs, i) -> new AreaPoligono(
                                rs.getString("codigo_imovel"), rs.getBigDecimal("hectares")), arquivoId));

        List<Cruzamento> cruzamentos = arquivoId == null ? List.of()
                : jdbcTemplate.query(SQL_CRUZAMENTOS_GRAVADOS, (rs, i) -> new Cruzamento(
                        rs.getString("codigo_imovel"),
                        categoriaPorFonte.get(rs.getString("fonte_camada")),
                        rs.getString("fonte_camada"),
                        rs.getString("codigo_alerta_app"),
                        rs.getBigDecimal("hectares_sobrepostos")), arquivoId);

        BigDecimal areaTotal = poligonos.stream()
                .map(AreaPoligono::areaHectares)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // reaproveita as somas do resultado do cálculo; toda categoria aparece, mesmo zerada
        ResultadoCalculoAnalitico resultado =
                new ResultadoCalculoAnalitico(poligonos.size(), areaTotal, cruzamentos, List.of());
        Map<Categoria, TotalCategoria> totais = new EnumMap<>(Categoria.class);
        for (Categoria categoria : Categoria.values()) {
            totais.put(categoria, new TotalCategoria(
                    resultado.contar(categoria), resultado.hectaresPorCategoria(categoria)));
        }

        List<Intersecao> intersecoes = cruzamentos.stream()
                .map(c -> new Intersecao(c.codigoImovel(), c.categoria(), c.fonteCamada(),
                        c.codigoAlerta(), c.hectaresSobrepostos()))
                .toList();

        return new CalculosCargaResponse(
                processoId,
                arquivoId,
                processo.get(0).etapa(),
                processo.get(0).situacao(),
                poligonos.size(),
                areaTotal,
                poligonos,
                totais,
                intersecoes,
                intersecoes.stream().filter(i -> i.categoria() == Categoria.ALERTA).toList());
    }
}
