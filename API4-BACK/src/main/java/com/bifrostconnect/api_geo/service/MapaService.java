package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.dto.FeicaoDetalheResponseDTO;
import com.bifrostconnect.api_geo.entity.CamadaReferencia;
import com.bifrostconnect.api_geo.entity.LogValidacao;
import com.bifrostconnect.api_geo.repository.CamadaReferenciaRepository;
import com.bifrostconnect.api_geo.repository.LogValidacaoRepository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class MapaService {

    private final LogValidacaoRepository logValidacaoRepository;
    private final CamadaReferenciaRepository camadaReferenciaRepository;
    private final JdbcTemplate jdbcTemplate;

    public MapaService(
            LogValidacaoRepository logValidacaoRepository,
            CamadaReferenciaRepository camadaReferenciaRepository,
            JdbcTemplate jdbcTemplate) {
        this.logValidacaoRepository = logValidacaoRepository;
        this.camadaReferenciaRepository = camadaReferenciaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public FeicaoDetalheResponseDTO buscarDetalhesPorId(Long idPoligono) {

        Optional<LogValidacao> optLog = logValidacaoRepository.findById(idPoligono);
        if (optLog.isPresent()) {
            LogValidacao log = optLog.get();

            Map<String, Object> dados = new HashMap<>();
            dados.put("id", log.getId());
            dados.put("processoId",
                    log.getProcesso() != null ? log.getProcesso().getId() : null);
            dados.put("identificadorFeicao", log.getIdentificadorFeicao());
            dados.put("sridOrigem", log.getSridOrigem());
            dados.put("geometriaValida", log.getGeomValida());
            dados.put("motivoInvalidez", log.getMotivoInvalidez());
            dados.put("severidade", log.getSeveridade());
            dados.put("mensagem", log.getMensagem());
            dados.put("dataDeteccao", log.getDataDeteccao());

            Map<String, Object> calculos = new HashMap<>();

            if (log.getGeom() == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Este registro possui apenas geometria bruta; "
                        + "não é possível calcular sua área ou consultar "
                        + "interseções espaciais."
                );
            }

            Double hectares = logValidacaoRepository
                    .calcularAreaEmHectares(log.getId());

            if (hectares == null) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Não foi possível calcular a área da geometria."
                );
            }

            calculos.put("areaHectares", hectares);

            List<CamadaReferencia> referencias =
                    camadaReferenciaRepository.buscarSobreposicoes(
                            List.of(
                                    "INCAR_AREA_IMOVEL",
                                    "SFB_FLORESTA_PUBLICA",
                                    "MMA_UC_CNUC",
                                    "INCRA_QUILOMBOLA",
                                    "INCRA_ASSENTAMENTO",
                                    "ICMBIO_UC_FEDERAL",
                                    "IBGE_MUNICIPIO",
                                    "IBGE_BIOMA_250MIL",
                                    "IBGE_BIOMA_5000MIL",
                                    "INPE_DESMATAMENTO_MATA_ATLANTICA",
                                    "INPE_DESMATAMENTO_CERRADO",
                                    "INPE_QUEIMADA",
                                    "IBAMA_EMBARGO",
                                    "FUNAI_TERRA_INDIGENA"
                            ),
                            log.getGeom()
                    );

            List<Map<String, Object>> referenciasHistoricas = referencias.stream()
                    .map(referencia -> {
                        Map<String, Object> item = new HashMap<>();
                        item.put("id", referencia.getId());
                        item.put("fonte", referencia.getFonte());
                        item.put("nomeCamada", referencia.getNomeCamada());
                        item.put("identificadorOrigem",
                                referencia.getIdentificadorOrigem());
                        item.put("dataReferencia",
                                referencia.getDataReferencia());
                        item.put("atributos", referencia.getAtributos());
                        return item;
                    })
                    .toList();

            calculos.put("referenciasHistoricas", referenciasHistoricas);
            calculos.put("quantidadeReferencias", referenciasHistoricas.size());

            return FeicaoDetalheResponseDTO.builder()
                    .id(log.getId())
                    .dadosAlfanumericos(dados)
                    .calculos(calculos)
                    .build();
        }

        // Fallback: consulta feicao_carga (polígonos da carga exibidos no mapa)
        String sqlFeicao = """
                SELECT fc.id, fc.codigo_imovel, ap.processo_id, p.ano_safra,
                       ROUND((ST_Area(fc.geom::geography) / 10000)::numeric, 4) AS area_ha
                FROM feicao_carga fc
                JOIN arquivo_processado ap ON ap.id = fc.arquivo_processado_id
                JOIN processo p ON p.id = ap.processo_id
                WHERE fc.id = ?
                """;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlFeicao, idPoligono);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Feição não encontrada.");
        }

        Map<String, Object> fcRow = rows.get(0);
        Long processoId = ((Number) fcRow.get("processo_id")).longValue();
        String codImovel = (String) fcRow.get("codigo_imovel");
        Number areaHa = (Number) fcRow.get("area_ha");

        Map<String, Object> dados = new HashMap<>();
        dados.put("id", idPoligono);
        dados.put("processoId", processoId);
        dados.put("identificadorFeicao", codImovel);
        dados.put("codigoImovel", codImovel);
        dados.put("anoSafra", fcRow.get("ano_safra"));
        dados.put("geometriaValida", true);
        dados.put("severidade", "INFO");
        dados.put("mensagem", "Geometria tratada e válida.");

        Map<String, Object> calculos = new HashMap<>();
        calculos.put("areaHectares", areaHa != null ? areaHa.doubleValue() : 0.0);

        List<Map<String, Object>> referenciasHistoricas = jdbcTemplate.queryForList("""
                SELECT fonte_camada AS fonte, hectares_sobrepostos, codigo_alerta_app
                FROM resultados_calculos
                WHERE processo_id = ? AND codigo_imovel = ?
                """, processoId, codImovel);

        calculos.put("referenciasHistoricas", referenciasHistoricas);
        calculos.put("quantidadeReferencias", referenciasHistoricas.size());

        return FeicaoDetalheResponseDTO.builder()
                .id(idPoligono)
                .dadosAlfanumericos(dados)
                .calculos(calculos)
                .build();
    }
}
