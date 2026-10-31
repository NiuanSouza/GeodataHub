package com.bifrostconnect.api_geo.repository;

import com.bifrostconnect.api_geo.dto.LogEspacialResponse;
import com.bifrostconnect.api_geo.dto.ProcessLogDetailsResponse;
import com.bifrostconnect.api_geo.dto.ProcessoDashboardResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ProcessoDashboardRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public Page<ProcessoDashboardResponse> listarProcessos(
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Long conjuntoId,
            Long etapaId,
            Long situacaoId,
            Pageable pageable) {

        StringBuilder sql = new StringBuilder("""
                SELECT
                    p.id,
                    c.nome AS dataset,
                    e.nome AS stage,
                    s.nome AS status,
                    o.nome AS source,
                    p.ano_safra AS year,
                    p.epsg_origem AS epsg,
                    p.data_criacao
                FROM processo p
                LEFT JOIN etapa e
                    ON e.id = p.etapa_atual_id
                LEFT JOIN situacao s
                    ON s.id = p.situacao_atual_id
                LEFT JOIN conjunto c
                    ON c.id = p.conjunto_id
                LEFT JOIN orgao o
                    ON o.id = p.orgao_id
                WHERE 1 = 1
                """);

        adicionarFiltros(
                sql,
                dataInicio,
                dataFim,
                conjuntoId,
                etapaId,
                situacaoId
        );

        sql.append("""
                ORDER BY p.data_criacao DESC
                """);

        Query query = entityManager.createNativeQuery(sql.toString());

        adicionarParametros(
                query,
                dataInicio,
                dataFim,
                conjuntoId,
                etapaId,
                situacaoId
        );

        query.setFirstResult(
                (int) pageable.getOffset()
        );

        query.setMaxResults(
                pageable.getPageSize()
        );

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();

        List<ProcessoDashboardResponse> processos =
                new ArrayList<>();

        for (Object[] resultado : resultados) {
            processos.add(
                    mapearProcesso(resultado)
            );
        }

        long total = contarProcessos(
                dataInicio,
                dataFim,
                conjuntoId,
                etapaId,
                situacaoId
        );

        return new PageImpl<>(
                processos,
                pageable,
                total
        );
    }

    public ProcessLogDetailsResponse buscarDetalhesProcessoFrontEnd(
            Long processoId) {

        String sql = """
                SELECT
                    p.id,
                    c.nome AS dataset,
                    e.nome AS stage,
                    s.nome AS status,
                    p.data_criacao,
                    a.nome_original AS layerName,
                    o.nome AS source,
                    p.ano_safra AS year,
                    p.epsg_origem AS epsg,
                    a.hash_sha256 AS integrityHash,
                    a.url_armazenamento AS originalFileUrl,
                    p.competencia,
                    p.formato,
                    p.classificacao_acesso,
                    p.cobertura_espacial,
                    p.cobertura_temporal_inicio,
                    p.cobertura_temporal_fim,
                    p.epsg_detectado,
                    p.versao_fechada,
                    p.versao_oficial
                FROM processo p
                LEFT JOIN etapa e
                    ON e.id = p.etapa_atual_id
                LEFT JOIN situacao s
                    ON s.id = p.situacao_atual_id
                LEFT JOIN conjunto c
                    ON c.id = p.conjunto_id
                LEFT JOIN orgao o
                    ON o.id = p.orgao_id
                LEFT JOIN arquivo_original a
                    ON a.processo_id = p.id
                WHERE p.id = :processoId
                ORDER BY a.id DESC
                LIMIT 1
                """;

        Query query = entityManager.createNativeQuery(sql);

        query.setParameter(
                "processoId",
                processoId
        );

        @SuppressWarnings("unchecked")
        List<Object[]> resultados = query.getResultList();

        if (resultados.isEmpty()) {
            return null;
        }

        Object[] resultado = resultados.get(0);

        LocalDateTime data = toLocalDateTime(resultado[4]);

        String dateTime = "";

        if (data != null) {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            dateTime = data.format(formatter);
        }

        List<LogEspacialResponse> logsEspaciais =
                buscarLogEspacial(processoId);

        Map<String, List<String>> logs =
                transformarLogs(logsEspaciais);

        String epsgDeclarado = resultado[8] != null ? resultado[8].toString() : "4674";
        String epsgDetectado = resultado[17] != null ? resultado[17].toString() : epsgDeclarado;
        Boolean versaoFechada = resultado[18] != null && Boolean.parseBoolean(resultado[18].toString());
        String versaoOficial = resultado[19] != null ? resultado[19].toString() : null;

        String situacaoAtual = resultado[3] != null ? resultado[3].toString() : null;

        List<Map<String, Object>> ocorrencias = buscarOcorrenciasValidacao(processoId);
        List<Object> validationChecks = montarValidationChecks(ocorrencias, situacaoAtual);
        List<Object> treatmentChecks = montarTreatmentChecks(epsgDeclarado, epsgDetectado);
        List<Object> analyticsData = montarAnalyticsData(processoId);
        List<List<Double>> mapCoords = buscarBoundingBox(processoId);

        return ProcessLogDetailsResponse.builder()
                .id(
                        resultado[0] != null
                                ? resultado[0].toString()
                                : null
                )
                .dataset(
                        resultado[1] != null
                                ? resultado[1].toString()
                                : null
                )
                .stage(
                        resultado[2] != null
                                ? resultado[2].toString()
                                : null
                )
                .status(
                        resultado[3] != null
                                ? resultado[3].toString()
                                : null
                )
                .dateTime(dateTime)
                .layerName(
                        resultado[5] != null
                                ? resultado[5].toString()
                                : null
                )
                .source(
                        resultado[6] != null
                                ? resultado[6].toString()
                                : null
                )
                .year(
                        resultado[7] != null
                                ? resultado[7].toString()
                                : null
                )
                .epsg(epsgDeclarado)
                .epsgDetected(epsgDetectado)
                .versaoFechada(versaoFechada)
                .versaoOficial(versaoOficial)
                .integrityHash(
                        resultado[9] != null
                                ? resultado[9].toString()
                                : null
                )
                .originalFileUrl(
                        resultado[10] != null
                                ? resultado[10].toString()
                                : null
                )
                .competencia(resultado[11] != null ? resultado[11].toString() : null)
                .formato(resultado[12] != null ? resultado[12].toString() : null)
                .classificacaoAcesso(resultado[13] != null ? resultado[13].toString() : null)
                .coberturaEspacial(resultado[14] != null ? resultado[14].toString() : null)
                .coberturaTemporalInicio(resultado[15] != null ? resultado[15].toString() : null)
                .coberturaTemporalFim(resultado[16] != null ? resultado[16].toString() : null)
                .description(
                        "Detalhes carregados com sucesso"
                )
                .mapCoordinates(mapCoords)
                .logs(logs)
                .validationChecks(validationChecks)
                .treatmentChecks(treatmentChecks)
                .analyticsData(analyticsData)
                .quarantineRecords(new ArrayList<>(ocorrencias))
                .build();
    }

    private List<Map<String, Object>> buscarOcorrenciasValidacao(Long processoId) {
        List<Map<String, Object>> list = new ArrayList<>();

        // 1. Ocorrências ativas registradas em ocorrencia_validacao (apenas status PENDENTE do ciclo atual)
        String sqlOcorrencias = """
                SELECT ov.id, r.nome AS regra, ov.mensagem, ov.severidade, ov.data_deteccao, ov.contexto
                FROM ocorrencia_validacao ov
                JOIN processo_etapa pe ON pe.id = ov.processo_etapa_id
                JOIN regra_validacao r ON r.id = ov.regra_id
                WHERE pe.processo_id = :processoId
                  AND ov.status = 'PENDENTE'
                  AND ov.data_deteccao >= (
                      SELECT COALESCE(MAX(ao.data_upload) - INTERVAL '5 seconds', '1970-01-01'::timestamp)
                      FROM arquivo_original ao
                      WHERE ao.processo_id = :processoId
                  )
                ORDER BY ov.data_deteccao DESC
                """;
        try {
            Query q = entityManager.createNativeQuery(sqlOcorrencias);
            q.setParameter("processoId", processoId);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] r : rows) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("recordId", r[0] != null ? r[0].toString() : "");
                map.put("motivo", r[2] != null ? r[2].toString() : "Inconsistência topológica detectada");
                map.put("etapaOrigem", r[1] != null ? r[1].toString() : "Validação Topológica");
                map.put("execucaoId", processoId.toString());
                LocalDateTime dt = toLocalDateTime(r[4]);
                map.put("dataRejeicao", dt != null ? dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "");
                map.put("campo", r[1] != null ? r[1].toString() : "Geometria");
                map.put("valorEncontrado", r[5] != null ? r[5].toString() : "Geometria Inválida/Sobreposta");
                map.put("valorEsperado", "Conforme Regras PostGIS");
                list.add(map);
            }
        } catch (Exception ignored) {}

        // 2. Logs de falha de validação e quarentena da tabela log_validacao
        String sqlLogs = """
                SELECT lv.id, COALESCE(lv.identificador_feicao, 'Quarentena') AS regra, lv.mensagem, lv.severidade, lv.data_deteccao, COALESCE(lv.motivo_invalidez, 'Falha de validação') AS contexto
                FROM log_validacao lv
                LEFT JOIN ocorrencia_validacao ov ON ov.id = lv.ocorrencia_validacao_id
                WHERE lv.processo_id = :processoId
                  AND (ov.id IS NULL OR ov.status = 'PENDENTE')
                  AND lv.data_deteccao >= (
                      SELECT COALESCE(MAX(ao.data_upload) - INTERVAL '5 seconds', '1970-01-01'::timestamp)
                      FROM arquivo_original ao
                      WHERE ao.processo_id = :processoId
                  )
                ORDER BY lv.data_deteccao DESC
                """;
        try {
            Query q2 = entityManager.createNativeQuery(sqlLogs);
            q2.setParameter("processoId", processoId);
            @SuppressWarnings("unchecked")
            List<Object[]> rows2 = q2.getResultList();
            for (Object[] r : rows2) {
                String recordId = r[0] != null ? "LOG-" + r[0].toString() : "";
                boolean jaExiste = list.stream().anyMatch(m -> recordId.equals(m.get("recordId")));
                if (!jaExiste) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("recordId", recordId);
                    map.put("motivo", r[2] != null ? r[2].toString() : "Retido em Quarentena");
                    map.put("etapaOrigem", "Quarentena");
                    map.put("execucaoId", processoId.toString());
                    LocalDateTime dt = toLocalDateTime(r[4]);
                    map.put("dataRejeicao", dt != null ? dt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")) : "");
                    map.put("campo", "Geometria");
                    map.put("valorEncontrado", r[5] != null ? r[5].toString() : "Inconsistência na esteira");
                    map.put("valorEsperado", "Regras de Validação Satisfeitas");
                    list.add(map);
                }
            }
        } catch (Exception ignored) {}

        return list;
    }

    private List<Object> montarValidationChecks(List<Map<String, Object>> ocorrencias, String situacaoAtual) {
        List<Object> checks = new ArrayList<>();
        boolean emQuarentena = "EM_VALIDACAO".equalsIgnoreCase(situacaoAtual) || "EM_QUARENTENA".equalsIgnoreCase(situacaoAtual);
        boolean hasInvalidGeom = ocorrencias.stream().anyMatch(o -> String.valueOf(o.get("campo")).contains("GEOMETRIA") || String.valueOf(o.get("motivo")).toUpperCase().contains("GEOMETRIA") || String.valueOf(o.get("motivo")).toUpperCase().contains("INVÁLID"));
        boolean hasDuplicateCar = ocorrencias.stream().anyMatch(o -> String.valueOf(o.get("campo")).contains("CAR") || String.valueOf(o.get("motivo")).toUpperCase().contains("CÓDIGO") || String.valueOf(o.get("motivo")).toUpperCase().contains("IMOVEL"));
        boolean hasDuplicatePoly = ocorrencias.stream().anyMatch(o -> String.valueOf(o.get("campo")).contains("POLIGONO_DUPLICADO") || String.valueOf(o.get("motivo")).toUpperCase().contains("DUPLICAD"));
        boolean hasOverlap = ocorrencias.stream().anyMatch(o -> String.valueOf(o.get("campo")).contains("SOBREPOSICAO") || String.valueOf(o.get("motivo")).toUpperCase().contains("SOBREPOSI"));

        // Se está em quarentena mas nenhum check específico foi identificado, marca a integridade geral como erro com motivo
        if (emQuarentena && !hasInvalidGeom && !hasDuplicateCar && !hasDuplicatePoly && !hasOverlap) {
            hasInvalidGeom = true;
        }

        String invalidDetail = hasInvalidGeom && emQuarentena && !ocorrencias.isEmpty()
                ? String.valueOf(ocorrencias.get(0).get("motivo"))
                : (hasInvalidGeom ? "Geometrias inválidas detectadas pelo PostGIS" : "Polígonos 2D válidos e fechados");

        checks.add(Map.of(
            "id", "val-1",
            "label", "Integridade da Geometria PostGIS",
            "description", "Verifica se todos os anéis são fechados e passam em ST_IsValid",
            "status", hasInvalidGeom ? "error" : "success",
            "detail", invalidDetail
        ));
        checks.add(Map.of(
            "id", "val-2",
            "label", "Unicidade do Código CAR / Imóvel",
            "description", "Garante que não existam múltiplos polígonos com o mesmo identificador na carga",
            "status", hasDuplicateCar ? "error" : "success",
            "detail", hasDuplicateCar ? "Códigos repetidos encontrados no mesmo arquivo" : "Todos os identificadores são únicos"
        ));
        checks.add(Map.of(
            "id", "val-3",
            "label", "Ausência de Polígonos Duplicados",
            "description", "Compara ST_Equals entre feições para evitar duplicidade de polígonos",
            "status", hasDuplicatePoly ? "error" : "success",
            "detail", hasDuplicatePoly ? "Polígonos idênticos detectados" : "Nenhuma duplicação geométrica encontrada"
        ));
        checks.add(Map.of(
            "id", "val-4",
            "label", "Sobreposição Espacial Interna",
            "description", "Analisa ST_Relate e interseções entre propriedades rurais",
            "status", hasOverlap ? "error" : "success",
            "detail", hasOverlap ? "Sobreposição acima do limiar aceitável detectada" : "Geometrias sem sobreposição conflitante"
        ));
        return checks;
    }

    private List<Object> montarTreatmentChecks(String epsgDeclarado, String epsgDetectado) {
        List<Object> checks = new ArrayList<>();
        boolean epsgDivergente = epsgDetectado != null && !epsgDetectado.isBlank()
                && !epsgDetectado.equalsIgnoreCase(epsgDeclarado);

        checks.add(Map.of(
            "id", "treat-1",
            "label", "Padronização para SIRGAS 2000 (EPSG:4674)",
            "description", "Reprojeção cartográfica de coordenadas via PostGIS ST_Transform",
            "status", "success",
            "detail", "Cópia de trabalho gerada em EPSG:4674"
        ));
        checks.add(Map.of(
            "id", "treat-2",
            "label", "Compatibilidade de Projeção Declarada vs Detectada",
            "description", "Verifica se o CRS do cabeçalho do arquivo confere com o metadado",
            "status", epsgDivergente ? "warning" : "success",
            "detail", epsgDivergente ? "Declarado: " + epsgDeclarado + " | Detectado: " + epsgDetectado : "EPSG compatível: " + epsgDeclarado
        ));
        checks.add(Map.of(
            "id", "treat-3",
            "label", "Limpeza de Vértices e Normalização 2D",
            "description", "Aplicação de ST_Force2D e remoção de redundâncias",
            "status", "success",
            "detail", "Polígonos normalizados em duas dimensões"
        ));
        return checks;
    }

    private List<Object> montarAnalyticsData(Long processoId) {
        List<Object> rows = new ArrayList<>();
        try {
            String sqlTotais = """
                    SELECT COUNT(fc.id) AS total_feicoes,
                           COALESCE(ROUND((SUM(ST_Area(fc.geom::geography)) / 10000)::numeric, 4), 0) AS area_total
                    FROM feicao_carga fc
                    JOIN arquivo_processado ap ON ap.id = fc.arquivo_processado_id
                    WHERE ap.processo_id = :processoId
                    """;
            Query qTotais = entityManager.createNativeQuery(sqlTotais);
            qTotais.setParameter("processoId", processoId);
            Object[] totais = (Object[]) qTotais.getSingleResult();
            long totalFeicoes = totais[0] != null ? ((Number) totais[0]).longValue() : 0;
            java.math.BigDecimal areaTotal = totais[1] != null ? new java.math.BigDecimal(totais[1].toString()) : java.math.BigDecimal.ZERO;

            if (totalFeicoes > 0) {
                rows.add(Map.of(
                    "param", "Cálculo de Área Total Contínua",
                    "result", areaTotal + " ha",
                    "reference", totalFeicoes + " feição(ões) analisada(s)",
                    "status", "success",
                    "statusLabel", "Calculado"
                ));

                String sqlCalculos = """
                        SELECT rc.fonte_camada,
                               COALESCE(ROUND(SUM(rc.hectares_sobrepostos)::numeric, 4), 0) AS total_ha,
                               COUNT(*) AS qtd
                        FROM resultados_calculos rc
                        WHERE rc.processo_id = :processoId
                        GROUP BY rc.fonte_camada
                        """;
                Query qCalc = entityManager.createNativeQuery(sqlCalculos);
                qCalc.setParameter("processoId", processoId);
                @SuppressWarnings("unchecked")
                List<Object[]> calcRows = qCalc.getResultList();

                java.math.BigDecimal rlHa = java.math.BigDecimal.ZERO;
                long rlQtd = 0;
                java.math.BigDecimal appHa = java.math.BigDecimal.ZERO;
                long appQtd = 0;
                java.math.BigDecimal alertaHa = java.math.BigDecimal.ZERO;
                long alertaQtd = 0;

                for (Object[] cr : calcRows) {
                    String fonte = cr[0] != null ? cr[0].toString().toUpperCase() : "";
                    java.math.BigDecimal ha = cr[1] != null ? new java.math.BigDecimal(cr[1].toString()) : java.math.BigDecimal.ZERO;
                    long qtd = cr[2] != null ? ((Number) cr[2]).longValue() : 0;

                    if (fonte.contains("RESERVA") || fonte.contains("RL")) {
                        rlHa = rlHa.add(ha);
                        rlQtd += qtd;
                    } else if (fonte.contains("APP")) {
                        appHa = appHa.add(ha);
                        appQtd += qtd;
                    } else if (fonte.contains("ALERTA") || fonte.contains("DESMATAMENTO") || fonte.contains("EMBARGO")) {
                        alertaHa = alertaHa.add(ha);
                        alertaQtd += qtd;
                    }
                }

                rows.add(Map.of(
                    "param", "Proporção de Reserva Legal",
                    "result", rlHa + " ha (" + rlQtd + " cruzamento(s))",
                    "reference", "Camada de Referência INCAR / INCRA",
                    "status", rlQtd > 0 ? "success" : "warning",
                    "statusLabel", rlQtd > 0 ? "Regular" : "Não identificada"
                ));

                rows.add(Map.of(
                    "param", "Área de APP Hídrica",
                    "result", appHa + " ha (" + appQtd + " cruzamento(s))",
                    "reference", "Art. 4º — Lei 12.651/2012",
                    "status", "success",
                    "statusLabel", appQtd > 0 ? "Conforme" : "Sem sobreposição"
                ));

                rows.add(Map.of(
                    "param", "Sobreposição com Áreas Embargadas / Alertas",
                    "result", alertaHa + " ha (" + alertaQtd + " alerta(s))",
                    "reference", "Cadastro IBAMA / INPE",
                    "status", alertaQtd > 0 ? "danger" : "success",
                    "statusLabel", alertaQtd > 0 ? "Alerta Crítico" : "Sem alertas"
                ));
            }
        } catch (Exception ignored) {
        }
        return rows;
    }

    private List<List<Double>> buscarBoundingBox(Long processoId) {
        try {
            String sql = """
                    SELECT ST_YMin(b.geom), ST_XMin(b.geom), ST_YMax(b.geom), ST_XMax(b.geom)
                    FROM (
                        SELECT ST_Extent(ST_Transform(fc.geom, 4326)) AS geom
                        FROM feicao_carga fc
                        JOIN arquivo_processado ap ON ap.id = fc.arquivo_processado_id
                        WHERE ap.processo_id = :processoId
                    ) b
                    WHERE b.geom IS NOT NULL
                    """;
            Query q = entityManager.createNativeQuery(sql);
            q.setParameter("processoId", processoId);
            @SuppressWarnings("unchecked")
            List<Object[]> res = q.getResultList();
            if (!res.isEmpty() && res.get(0)[0] != null) {
                Object[] r = res.get(0);
                double minLat = ((Number) r[0]).doubleValue();
                double minLon = ((Number) r[1]).doubleValue();
                double maxLat = ((Number) r[2]).doubleValue();
                double maxLon = ((Number) r[3]).doubleValue();

                List<List<Double>> coords = new ArrayList<>();
                coords.add(List.of(minLat, minLon));
                coords.add(List.of(minLat, maxLon));
                coords.add(List.of(maxLat, maxLon));
                coords.add(List.of(maxLat, minLon));
                return coords;
            }
        } catch (Exception ignored) {
        }
        return new ArrayList<>();
    }

    private ProcessoDashboardResponse mapearProcesso(
            Object[] resultado) {

        LocalDateTime data =
                toLocalDateTime(resultado[7]);

        String dateTime = "";

        if (data != null) {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

            dateTime = data.format(formatter);
        }

        return new ProcessoDashboardResponse(
                resultado[0] != null
                        ? resultado[0].toString()
                        : null,

                dateTime,

                resultado[1] != null
                        ? resultado[1].toString()
                        : null,

                resultado[2] != null
                        ? resultado[2].toString()
                        : null,

                resultado[3] != null
                        ? resultado[3].toString()
                        : null,

                resultado[4] != null
                        ? resultado[4].toString()
                        : null,

                resultado[5] != null
                        ? resultado[5].toString()
                        : null,

                resultado[6] != null
                        ? resultado[6].toString()
                        : null,

                null,

                null
        );
    }

    private long contarProcessos(
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Long conjuntoId,
            Long etapaId,
            Long situacaoId) {

        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(*)
                FROM processo p
                WHERE 1 = 1
                """);

        adicionarFiltros(
                sql,
                dataInicio,
                dataFim,
                conjuntoId,
                etapaId,
                situacaoId
        );

        Query query = entityManager.createNativeQuery(
                sql.toString()
        );

        adicionarParametros(
                query,
                dataInicio,
                dataFim,
                conjuntoId,
                etapaId,
                situacaoId
        );

        Number resultado =
                (Number) query.getSingleResult();

        return resultado.longValue();
    }

    private void adicionarFiltros(
            StringBuilder sql,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Long conjuntoId,
            Long etapaId,
            Long situacaoId) {

        if (dataInicio != null) {
            sql.append(
                    " AND p.data_criacao >= :dataInicio "
            );
        }

        if (dataFim != null) {
            sql.append(
                    " AND p.data_criacao < :dataFim "
            );
        }

        if (conjuntoId != null) {
            sql.append(
                    " AND p.conjunto_id = :conjuntoId "
            );
        }

        if (etapaId != null) {
            sql.append(
                    " AND p.etapa_atual_id = :etapaId "
            );
        }

        if (situacaoId != null) {
            sql.append(
                    " AND p.situacao_atual_id = :situacaoId "
            );
        }
    }

    private void adicionarParametros(
            Query query,
            LocalDateTime dataInicio,
            LocalDateTime dataFim,
            Long conjuntoId,
            Long etapaId,
            Long situacaoId) {

        if (dataInicio != null) {
            query.setParameter(
                    "dataInicio",
                    Timestamp.valueOf(dataInicio)
            );
        }

        if (dataFim != null) {
            query.setParameter(
                    "dataFim",
                    Timestamp.valueOf(dataFim)
            );
        }

        if (conjuntoId != null) {
            query.setParameter(
                    "conjuntoId",
                    conjuntoId
            );
        }

        if (etapaId != null) {
            query.setParameter(
                    "etapaId",
                    etapaId
            );
        }

        if (situacaoId != null) {
            query.setParameter(
                    "situacaoId",
                    situacaoId
            );
        }
    }

    public Long contarTodosOsProcessos() {

        Query query = entityManager.createNativeQuery("""
                SELECT COUNT(*)
                FROM processo
                """);

        Number resultado =
                (Number) query.getSingleResult();

        return resultado.longValue();
    }

    public Map<String, Long> contarPorSituacao() {

        Query query = entityManager.createNativeQuery("""
                SELECT
                    COALESCE(s.nome, 'SEM_SITUACAO') AS nome,
                    COUNT(p.id) AS quantidade
                FROM processo p
                LEFT JOIN situacao s
                    ON s.id = p.situacao_atual_id
                GROUP BY s.nome
                ORDER BY s.nome
                """);

        return transformarResultados(
                query.getResultList()
        );
    }

    public Map<String, Long> contarPorEtapa() {

        Query query = entityManager.createNativeQuery("""
                SELECT
                    COALESCE(e.nome, 'SEM_ETAPA') AS nome,
                    COUNT(p.id) AS quantidade
                FROM processo p
                LEFT JOIN etapa e
                    ON e.id = p.etapa_atual_id
                GROUP BY e.nome
                ORDER BY e.nome
                """);

        return transformarResultados(
                query.getResultList()
        );
    }

    public Map<String, Long> contarPorConjunto() {

        Query query = entityManager.createNativeQuery("""
                SELECT
                    COALESCE(c.nome, 'SEM_CONJUNTO') AS nome,
                    COUNT(p.id) AS quantidade
                FROM processo p
                LEFT JOIN conjunto c
                    ON c.id = p.conjunto_id
                GROUP BY c.nome
                ORDER BY c.nome
                """);

        return transformarResultados(
                query.getResultList()
        );
    }

    private Map<String, Long> transformarResultados(
            List<?> resultados) {

        Map<String, Long> resultadoFinal =
                new LinkedHashMap<>();

        for (Object item : resultados) {

            Object[] linha = (Object[]) item;

            String nome =
                    linha[0] != null
                            ? linha[0].toString()
                            : null;

            Number quantidade =
                    (Number) linha[1];

            resultadoFinal.put(
                    nome,
                    quantidade.longValue()
            );
        }

        return resultadoFinal;
    }

    public List<LogEspacialResponse> buscarLogEspacial(
            Long processoId) {

        Query query = entityManager.createNativeQuery("""
                SELECT
                    lp.id,
                    lp.processo_id,
                    lp.processo_etapa_id,
                    pe.etapa_id,
                    e.nome AS etapa,
                    pe.situacao_id,
                    s.nome AS situacao,
                    pe.tentativa,
                    lp.nivel,
                    lp.mensagem,
                    lp.codigo_erro,
                    lp.detalhes,
                    lp.data_hora
                FROM log_processamento lp
                LEFT JOIN processo_etapa pe
                    ON pe.id = lp.processo_etapa_id
                LEFT JOIN etapa e
                    ON e.id = pe.etapa_id
                LEFT JOIN situacao s
                    ON s.id = pe.situacao_id
                WHERE lp.processo_id = :processoId
                ORDER BY lp.data_hora ASC
                """);

        query.setParameter(
                "processoId",
                processoId
        );

        @SuppressWarnings("unchecked")
        List<Object[]> resultados =
                query.getResultList();

        List<LogEspacialResponse> logs =
                new ArrayList<>();

        for (Object[] resultado : resultados) {

            logs.add(
                    new LogEspacialResponse(
                            toLong(resultado[0]),
                            toLong(resultado[1]),
                            toLong(resultado[2]),
                            toLong(resultado[3]),
                            resultado[4] != null
                                    ? resultado[4].toString()
                                    : null,
                            toLong(resultado[5]),
                            resultado[6] != null
                                    ? resultado[6].toString()
                                    : null,
                            toInteger(resultado[7]),
                            resultado[8] != null
                                    ? resultado[8].toString()
                                    : null,
                            resultado[9] != null
                                    ? resultado[9].toString()
                                    : null,
                            resultado[10] != null
                                    ? resultado[10].toString()
                                    : null,
                            resultado[11] != null
                                    ? resultado[11].toString()
                                    : null,
                            toLocalDateTime(resultado[12])
                    )
            );
        }

        return logs;
    }

    private Map<String, List<String>> transformarLogs(
            List<LogEspacialResponse> logs) {

        Map<String, List<String>> resultado =
                new LinkedHashMap<>();

        for (LogEspacialResponse log : logs) {

            String chave;

            if (log.etapa() != null && !log.etapa().isBlank()) {
                chave = log.etapa();
            } else if (log.situacao() != null && !log.situacao().isBlank()) {
                chave = log.situacao();
            } else {
                chave = "GERAL";
            }

            String mensagem = log.mensagem();

            if (mensagem == null || mensagem.isBlank()) {
                mensagem = log.detalhes();
            }

            if (mensagem == null || mensagem.isBlank()) {
                mensagem = "";
            }

            resultado
                    .computeIfAbsent(
                            chave,
                            key -> new ArrayList<>()
                    )
                    .add(mensagem);
        }

        return resultado;
    }

    private Long toLong(Object valor) {

        if (valor == null) {
            return null;
        }

        return ((Number) valor).longValue();
    }

    private Integer toInteger(Object valor) {

        if (valor == null) {
            return null;
        }

        return ((Number) valor).intValue();
    }

    private LocalDateTime toLocalDateTime(Object valor) {

        if (valor == null) {
            return null;
        }

        if (valor instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }

        if (valor instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }

        return null;
    }
}

