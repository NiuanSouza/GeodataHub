package com.bifrostconnect.api_geo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RevisaoPublicacaoService {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, Object> obterDadosRevisao(Long idCarga) {
        // 1. Verifica se a carga/processo existe
        String sqlCheck = "SELECT COUNT(*) FROM processo WHERE id = ?";
        Integer count = jdbcTemplate.queryForObject(sqlCheck, Integer.class, idCarga);
        if (count == null || count == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carga/Processo não encontrado.");
        }

        // 2. Busca o resumo de auditoria e cálculos do processo
        String sqlResumo = """
            SELECT p.id as processo_id, p.ano_safra, o.nome as orgao, c.nome as conjunto, 
                   (SELECT COUNT(*) FROM feicao_carga fc 
                    JOIN arquivo_processado ap ON fc.arquivo_processado_id = ap.id 
                    WHERE ap.processo_id = p.id) as total_feicoes
            FROM processo p
            LEFT JOIN orgao o ON p.orgao_id = o.id
            LEFT JOIN conjunto c ON p.conjunto_id = c.id
            WHERE p.id = ?
            """;

        Map<String, Object> resumo = jdbcTemplate.queryForMap(sqlResumo, idCarga);

        // 3. Constrói a estrutura do GeoJSON FeatureCollection combinando as geometrias salvas (amostra de até 500 para prévia responsiva)
        String sqlGeoJson = """
            SELECT json_build_object(
                'type', 'FeatureCollection',
                'features', COALESCE(json_agg(
                    json_build_object(
                        'type', 'Feature',
                        'geometry', ST_AsGeoJSON(ST_Transform(fc.geom, 4326), 6)::json,
                        'properties', json_build_object(
                            'id', fc.id,
                            'identificador', fc.codigo_imovel,
                            'codigoImovel', fc.codigo_imovel,
                            'areaHectares', ROUND((ST_Area(fc.geom::geography) / 10000)::numeric, 4),
                            'atributos', fc.atributos
                        )
                    )
                ), '[]'::json)
            ) as geojson
            FROM (
                SELECT fc.id, fc.geom, fc.codigo_imovel, fc.atributos
                FROM feicao_carga fc
                JOIN arquivo_processado ap ON fc.arquivo_processado_id = ap.id
                WHERE ap.processo_id = ?
                LIMIT 500
            ) fc
            """;

        String geoJsonString = jdbcTemplate.queryForObject(sqlGeoJson, String.class, idCarga);

        // 4. Monta o payload final combinando o resumo e o GeoJSON
        Map<String, Object> resposta = new HashMap<>();
        resposta.put("resumoAuditoria", resumo);
        
        // Converte a string GeoJSON em objeto para o Jackson serializar corretamente
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        try {
            Object geoJsonObj = mapper.readValue(geoJsonString, Object.class);
            resposta.put("feicoesGeoJson", geoJsonObj);
        } catch (Exception e) {
            resposta.put("feicoesGeoJson", null);
        }

        return resposta;
    }
}