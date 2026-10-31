package com.bifrostconnect.api_geo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CamadasMapaService {

    private static final int TAMANHO_MAXIMO_PAGINA = 200;

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> buscarCamadasPorFiltros(
            Long conjuntoId,
            String anoSafra,
            String bbox,
            int page,
            int size,
            String busca) {
        return buscarCamadasPorFiltros(conjuntoId, anoSafra, bbox, page, size, busca, null);
    }

    public Map<String, Object> buscarCamadasPorFiltros(
            Long conjuntoId,
            String anoSafra,
            String bbox,
            int page,
            int size,
            String busca,
            Long processoId) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O parâmetro 'page' não pode ser negativo."
            );
        }

        if (size < 1 || size > TAMANHO_MAXIMO_PAGINA) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O parâmetro 'size' deve estar entre 1 e "
                            + TAMANHO_MAXIMO_PAGINA + "."
            );
        }

        List<Object> filtroParams = new ArrayList<>();

        StringBuilder filtros = new StringBuilder("WHERE 1=1\n");

        if (processoId != null) {
            // Quando consultado especificamente por processoId (ex.: prévia na tela da carga/revisão),
            // permite visualizar as feições mesmo antes da publicação final.
            filtros.append(" AND p.id = ?\n");
            filtroParams.add(processoId);
        } else {
            filtros.append("""
                  AND (p.versao_fechada = TRUE OR (e.nome = 'PUBLICACAO' AND s.nome = 'CONCLUIDA'))
                """);
        }

        if (conjuntoId != null) {
            filtros.append(" AND p.conjunto_id = ?");
            filtroParams.add(conjuntoId);
        }

        if (anoSafra != null && !anoSafra.isBlank()) {
            filtros.append(" AND p.ano_safra = ?");
            filtroParams.add(anoSafra.trim());
        }

        if (busca != null && !busca.isBlank()) {
            filtros.append("""
                     AND (
                         fc.codigo_imovel ILIKE ?
                         OR p.ano_safra ILIKE ?
                         OR CAST(p.id AS TEXT) ILIKE ?
                         OR c.nome ILIKE ?
                         OR o.nome ILIKE ?
                         OR o.sigla ILIKE ?
                     )
                    """);

            String termo = "%" + busca.trim() + "%";

            filtroParams.add(termo);
            filtroParams.add(termo);
            filtroParams.add(termo);
            filtroParams.add(termo);
            filtroParams.add(termo);
            filtroParams.add(termo);
        }

        if (bbox != null && !bbox.isBlank()) {
            String[] coordenadas = bbox.split(",");

            if (coordenadas.length != 4) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "O parâmetro 'bbox' deve conter minLon,minLat,maxLon,maxLat."
                );
            }

            double minLon;
            double minLat;
            double maxLon;
            double maxLat;

            try {
                minLon = Double.parseDouble(coordenadas[0].trim());
                minLat = Double.parseDouble(coordenadas[1].trim());
                maxLon = Double.parseDouble(coordenadas[2].trim());
                maxLat = Double.parseDouble(coordenadas[3].trim());
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "As coordenadas de 'bbox' devem ser numéricas."
                );
            }

            if (!Double.isFinite(minLon)
                    || !Double.isFinite(minLat)
                    || !Double.isFinite(maxLon)
                    || !Double.isFinite(maxLat)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "As coordenadas de 'bbox' devem ser números válidos."
                );
            }

            if (minLon >= maxLon || minLat >= maxLat) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Os limites de 'bbox' devem estar em ordem crescente."
                );
            }

            if (minLon < -180 || maxLon > 180
                    || minLat < -90 || maxLat > 90) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "As coordenadas de 'bbox' devem estar nos limites geográficos válidos."
                );
            }

            filtros.append("""
                     AND fc.geom && ST_Transform(
                         ST_MakeEnvelope(?, ?, ?, ?, 4326),
                         4674
                     )
                    """);

            filtroParams.add(minLon);
            filtroParams.add(minLat);
            filtroParams.add(maxLon);
            filtroParams.add(maxLat);
        }

        String from = """
                FROM feicao_carga fc
                JOIN arquivo_processado ap
                    ON fc.arquivo_processado_id = ap.id
                JOIN processo p
                    ON ap.processo_id = p.id
                JOIN etapa e
                    ON e.id = p.etapa_atual_id
                JOIN situacao s
                    ON s.id = p.situacao_atual_id
                LEFT JOIN conjunto c
                    ON c.id = p.conjunto_id
                LEFT JOIN orgao o
                    ON o.id = p.orgao_id
                """;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) " + from + filtros,
                Long.class,
                filtroParams.toArray()
        );

        long deslocamento = (long) page * size;

        List<Object> dadosParams = new ArrayList<>(filtroParams);
        dadosParams.add(size);
        dadosParams.add(deslocamento);

        String sqlGeoJson = """
                SELECT json_build_object(
                    'type', 'FeatureCollection',
                    'features', COALESCE(
                        json_agg(
                            json_build_object(
                                'type', 'Feature',
                                'geometry', ST_AsGeoJSON(ST_Transform(resultado.geom, 4326), 6)::json,
                                'properties', json_build_object(
                                    'id', resultado.id,
                                    'processoId', resultado.processo_id,
                                    'codigoImovel', resultado.codigo_imovel,
                                    'anoSafra', resultado.ano_safra,
                                    'areaHectares', resultado.area_hectares,
                                    'name', resultado.base_nome,
                                    'category', resultado.base_nome,
                                    'source', resultado.orgao_nome
                                )
                            )
                            ORDER BY resultado.id
                        ),
                        '[]'::json
                    )
                )::text
                FROM (
                    SELECT
                        fc.id,
                        fc.geom,
                        fc.codigo_imovel,
                        p.id AS processo_id,
                        p.ano_safra,
                        COALESCE(c.nome, o.nome, 'Carga #' || p.id) AS base_nome,
                        COALESCE(o.nome, o.sigla, 'Fonte Oficial') AS orgao_nome,
                        ROUND((ST_Area(fc.geom::geography) / 10000)::numeric, 4) AS area_hectares
                """ + from + filtros + """
                    ORDER BY fc.id
                    LIMIT ? OFFSET ?
                ) resultado
                """;

        String geoJsonString = jdbcTemplate.queryForObject(
                sqlGeoJson,
                String.class,
                dadosParams.toArray()
        );

        Map<String, Object> resposta;

        try {
            resposta = new ObjectMapper().readValue(
                    geoJsonString,
                    Map.class
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "Não foi possível montar o GeoJSON das camadas.",
                    ex
            );
        }

        resposta = new LinkedHashMap<>(resposta);

        resposta.put("page", page);
        resposta.put("size", size);
        resposta.put("totalElements", total == null ? 0L : total);
        resposta.put(
                "totalPages",
                total == null || total == 0
                        ? 0L
                        : (total + size - 1) / size
        );

        return resposta;
    }

    /**
     * Retorna os locais (pontos/centroides e metadados) das bases oficiais publicadas
     * para alimentar a lista de "Bases cadastradas" e os marcadores do mapa operacional.
     */
    public List<Map<String, Object>> listarLocaisBasesPublicadas() {
        String sql = """
            SELECT 
                CAST(p.id AS TEXT) AS id,
                COALESCE(c.nome, o.nome, 'Carga #' || p.id) AS name,
                COALESCE(o.sigla || ' (' || p.ano_safra || ')', c.nome, 'Base Oficial') AS type,
                'Concluída' AS status,
                COALESCE(
                    ST_Y(ST_Centroid(ST_Extent(ST_Transform(fc.geom, 4326))::geometry)),
                    -15.7800
                ) AS latitude,
                COALESCE(
                    ST_X(ST_Centroid(ST_Extent(ST_Transform(fc.geom, 4326))::geometry)),
                    -47.9300
                ) AS longitude,
                COUNT(fc.id) AS total_feicoes
            FROM processo p
            JOIN etapa e ON e.id = p.etapa_atual_id
            JOIN situacao s ON s.id = p.situacao_atual_id
            LEFT JOIN conjunto c ON c.id = p.conjunto_id
            LEFT JOIN orgao o ON o.id = p.orgao_id
            LEFT JOIN arquivo_processado ap ON ap.processo_id = p.id
            LEFT JOIN feicao_carga fc ON fc.arquivo_processado_id = ap.id
            WHERE (p.versao_fechada = TRUE OR (e.nome = 'PUBLICACAO' AND s.nome = 'CONCLUIDA'))
            GROUP BY p.id, c.nome, o.nome, o.sigla, p.ano_safra, s.nome
            ORDER BY p.id DESC
            """;
        try {
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("Erro ao listar locais de bases publicadas: {}", e.getMessage(), e);
            return List.of();
        }
    }
}