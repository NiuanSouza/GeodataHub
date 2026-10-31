package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.Problema;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.TipoProblema;
import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.entity.OcorrenciaValidacao;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.entity.RegraValidacao;
import com.bifrostconnect.api_geo.repository.OcorrenciaValidacaoRepository;
import com.bifrostconnect.api_geo.repository.RegraValidacaoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

// 2ª parte do Tratamento: olha os polígonos da carga e caça geometria inválida,
// polígono duplicado e polígono encavalado em outro
@Service
public class ValidacaoTopologicaService {

    // menos de 1 m² de interseção a gente considera arredondamento, não sobreposição de verdade
    static final BigDecimal TOLERANCIA_SOBREPOSICAO_HA = new BigDecimal("0.0001");

    private static final String SQL_INVALIDAS = """
            SELECT codigo_imovel, ST_IsValidReason(geom) AS motivo
              FROM feicao_carga
             WHERE arquivo_processado_id = ?
               AND NOT ST_IsValid(geom)
             ORDER BY id
             LIMIT 100
            """;

    // a.id < b.id pra cada par aparecer uma vez só. O CASE é pra só comparar geometria válida,
    // porque com geometria inválida o PostGIS estoura TopologyException
    private static final String SQL_DUPLICADAS = """
            SELECT a.codigo_imovel AS codigo_a, b.codigo_imovel AS codigo_b
              FROM feicao_carga a
              JOIN feicao_carga b
                ON b.arquivo_processado_id = a.arquivo_processado_id
               AND a.id < b.id
               AND a.geom && b.geom
             WHERE a.arquivo_processado_id = ?
               AND CASE WHEN ST_IsValid(a.geom) AND ST_IsValid(b.geom)
                        THEN ST_Equals(a.geom, b.geom)
                        ELSE FALSE END
             ORDER BY a.id, b.id
             LIMIT 100
            """;

    // o ST_Relate '2********' só pega quando um entra na área do outro (encostar na divisa não conta).
    // O NOT ST_Equals é pra não repetir os duplicados, que já foram pegos na consulta de cima
    private static final String SQL_SOBREPOSTAS = """
            SELECT codigo_a, codigo_b, hectares
              FROM (SELECT a.id AS id_a, b.id AS id_b,
                           a.codigo_imovel AS codigo_a, b.codigo_imovel AS codigo_b,
                           ROUND((ST_Area(ST_CollectionExtract(ST_Intersection(a.geom, b.geom), 3)::geography)
                                  / 10000)::numeric, 4) AS hectares
                      FROM feicao_carga a
                      JOIN feicao_carga b
                        ON b.arquivo_processado_id = a.arquivo_processado_id
                       AND a.id < b.id
                       AND a.geom && b.geom
                     WHERE a.arquivo_processado_id = ?
                       AND CASE WHEN ST_IsValid(a.geom) AND ST_IsValid(b.geom)
                                THEN ST_Relate(a.geom, b.geom, '2********') AND NOT ST_Equals(a.geom, b.geom)
                                ELSE FALSE END) s
             WHERE s.hectares >= ?
             ORDER BY s.id_a, s.id_b
             LIMIT 100
            """;

    private static final String SQL_ETAPA_ABERTA = """
            SELECT id FROM processo_etapa
             WHERE processo_id = ? AND data_fim IS NULL
             ORDER BY id DESC
             LIMIT 1
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;
    private final FeicaoCargaService feicaoCargaService;
    private final RegraValidacaoRepository regraRepository;
    private final OcorrenciaValidacaoRepository ocorrenciaRepository;

    public ValidacaoTopologicaService(
            JdbcTemplate jdbcTemplate,
            FeicaoCargaService feicaoCargaService,
            RegraValidacaoRepository regraRepository,
            OcorrenciaValidacaoRepository ocorrenciaRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.feicaoCargaService = feicaoCargaService;
        this.regraRepository = regraRepository;
        this.ocorrenciaRepository = ocorrenciaRepository;
    }

    public ResultadoValidacaoTopologica validar(Processo processo, ArquivoProcessado copiaTrabalho) throws IOException {
        Long arquivoId = copiaTrabalho.getId();

        FeicaoCargaService.ResultadoCarga carga =
                feicaoCargaService.carregar(arquivoId, Path.of(copiaTrabalho.getUrlArmazenamento()));

        List<Problema> problemas = new ArrayList<>(carga.rejeitadas());
        // Tarefa 15: ST_IsValid/ST_Equals/ST_Intersection nas geometrias da carga; se o GEOS
        // estourar, vira FalhaGeoespacialException (422) e o Tratamento manda pra Quarentena
        problemas.addAll(ExecucaoGeoespacial.executar(jdbcTemplate, "validação topológica dos polígonos", () -> {
            List<Problema> encontrados = new ArrayList<>(buscarInvalidas(arquivoId));
            encontrados.addAll(buscarDuplicadas(arquivoId));
            encontrados.addAll(buscarSobrepostas(arquivoId));
            return encontrados;
        }));

        registrarOcorrencias(processo, problemas);

        return new ResultadoValidacaoTopologica(carga.totalFeicoes(), carga.carregadas(), problemas);
    }

    private List<Problema> buscarInvalidas(Long arquivoId) {
        return jdbcTemplate.query(SQL_INVALIDAS, (rs, i) -> Problema.daFeicao(
                TipoProblema.GEOMETRIA_INVALIDA,
                rs.getString("codigo_imovel"),
                "Geometria inválida: " + rs.getString("motivo")), arquivoId);
    }

    private List<Problema> buscarDuplicadas(Long arquivoId) {
        return jdbcTemplate.query(SQL_DUPLICADAS, (rs, i) -> new Problema(
                TipoProblema.POLIGONO_DUPLICADO,
                rs.getString("codigo_a"),
                rs.getString("codigo_b"),
                null,
                "Polígono " + rs.getString("codigo_a") + " tem geometria idêntica ao polígono "
                        + rs.getString("codigo_b") + "."), arquivoId);
    }

    private List<Problema> buscarSobrepostas(Long arquivoId) {
        return jdbcTemplate.query(SQL_SOBREPOSTAS, (rs, i) -> new Problema(
                TipoProblema.POLIGONO_SOBREPOSTO,
                rs.getString("codigo_a"),
                rs.getString("codigo_b"),
                rs.getBigDecimal("hectares"),
                "Polígono " + rs.getString("codigo_a") + " sobrepõe o polígono " + rs.getString("codigo_b")
                        + " em " + rs.getBigDecimal("hectares").toPlainString() + " ha."),
                arquivoId, TOLERANCIA_SOBREPOSICAO_HA);
    }

    private void registrarOcorrencias(Processo processo, List<Problema> problemas) {
        if (problemas.isEmpty()) {
            return;
        }
        Long processoEtapaId = jdbcTemplate.query(SQL_ETAPA_ABERTA,
                rs -> rs.next() ? rs.getLong(1) : null, processo.getId());
        if (processoEtapaId == null) {
            throw new IllegalStateException("Processo " + processo.getId() + " não possui etapa em andamento.");
        }

        Map<TipoProblema, RegraValidacao> regras = new HashMap<>();
        List<OcorrenciaValidacao> ocorrencias = new ArrayList<>(problemas.size());

        for (Problema problema : problemas) {
            RegraValidacao regra = regras.computeIfAbsent(problema.tipo(), tipo ->
                    regraRepository.findByNome(tipo.getRegra()).orElseThrow(() -> new IllegalStateException(
                            "Regra de validação não cadastrada: " + tipo.getRegra())));

            OcorrenciaValidacao ocorrencia = new OcorrenciaValidacao();
            ocorrencia.setProcesso(processo);
            ocorrencia.setRegra(regra);
            ocorrencia.setProcessoEtapaId(processoEtapaId);
            ocorrencia.setSeveridade(regra.getSeveridade());
            ocorrencia.setMensagem(problema.mensagem());
            ocorrencia.setDetalhes(detalhar(problema));
            ocorrencias.add(ocorrencia);
        }
        ocorrenciaRepository.saveAll(ocorrencias);
    }

    private String detalhar(Problema problema) {
        ObjectNode detalhes = mapper.createObjectNode();
        detalhes.put("tipo", problema.tipo().name());
        detalhes.put("codigo_imovel", problema.codigoImovel());
        if (problema.codigoImovelRelacionado() != null) {
            detalhes.put("codigo_imovel_relacionado", problema.codigoImovelRelacionado());
        }
        if (problema.hectaresSobrepostos() != null) {
            detalhes.put("hectares_sobrepostos", problema.hectaresSobrepostos());
        }
        return detalhes.toString();
    }
}
