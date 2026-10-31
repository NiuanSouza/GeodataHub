package com.bifrostconnect.api_geo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DominioService {

    private final JdbcTemplate jdbcTemplate;

    public DominioService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> buscarOpcoesFiltros() {
        List<Map<String, Object>> conjuntos = jdbcTemplate.query(
                "SELECT id, nome FROM conjunto WHERE ativo = true ORDER BY id",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("label", rs.getString("nome"));
                    return item;
                }
        );

        List<Map<String, Object>> etapas = jdbcTemplate.query(
                "SELECT id, nome FROM etapa ORDER BY ordem, id",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("label", formatarEtapa(rs.getString("nome")));
                    return item;
                }
        );

        List<Map<String, Object>> situacoes = jdbcTemplate.query(
                "SELECT id, nome FROM situacao ORDER BY id",
                (rs, rowNum) -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getLong("id"));
                    item.put("label", formatarSituacao(rs.getString("nome")));
                    return item;
                }
        );

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("conjuntos", conjuntos);
        resultado.put("etapas", etapas);
        resultado.put("situacoes", situacoes);
        return resultado;
    }

    public Map<String, Object> buscarOpcoesCarga() {
        List<String> anos = List.of("2026", "2025", "2024", "2023", "2022", "2021", "2020");

        List<Map<String, String>> epsgs = new ArrayList<>();
        epsgs.add(Map.of("id", "4326", "label", "EPSG:4326 (WGS 84)"));
        epsgs.add(Map.of("id", "4674", "label", "EPSG:4674 (SIRGAS 2000)"));

        List<Map<String, String>> orgaos = jdbcTemplate.query(
                "SELECT id, nome, sigla FROM orgao WHERE ativo = true ORDER BY id",
                (rs, rowNum) -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("id", String.valueOf(rs.getLong("id")));
                    String sigla = rs.getString("sigla");
                    String nome = rs.getString("nome");
                    item.put("label", (sigla != null && !sigla.isBlank()) ? sigla : nome);
                    return item;
                }
        );

        List<Map<String, String>> conjuntos = jdbcTemplate.query(
                "SELECT id, nome FROM conjunto WHERE ativo = true ORDER BY id",
                (rs, rowNum) -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("id", String.valueOf(rs.getLong("id")));
                    item.put("label", rs.getString("nome"));
                    return item;
                }
        );

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("anos", anos);
        resultado.put("epsgs", epsgs);
        resultado.put("orgaos", orgaos);
        resultado.put("conjuntos", conjuntos);
        return resultado;
    }

    private String formatarEtapa(String codigo) {
        if (codigo == null) return "";
        return switch (codigo.toUpperCase()) {
            case "INGESTAO" -> "Ingestão";
            case "TRATAMENTO" -> "Tratamento";
            case "VALIDACAO" -> "Validação";
            case "CALCULO_ANALITICO" -> "Cálculo Analítico";
            case "PUBLICACAO" -> "Publicação";
            default -> codigo;
        };
    }

    private String formatarSituacao(String codigo) {
        if (codigo == null) return "";
        return switch (codigo.toUpperCase()) {
            case "EM_ANDAMENTO" -> "Em andamento";
            case "EM_VALIDACAO", "AGUARDANDO_VALIDACAO" -> "Aguardando validação";
            case "COM_RESSALVA" -> "Com ressalva";
            case "FALHOU" -> "Falhou";
            case "CONCLUIDA" -> "Concluída";
            default -> codigo;
        };
    }
}
