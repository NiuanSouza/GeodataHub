package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeoJsonEpsgService {

    public static final String EPSG_PADRAO_SEM_CRS = "4326";


    private static final Pattern EPSG_PATTERN = Pattern.compile(
            "(?i)EPSG(?::|%3A|/)+(?:[0-9]+(?:\\.[0-9]+)*(?::|%3A|/)+)?([0-9]{4,6})(?![0-9])");

    private static final Pattern DOIS_PONTOS_PATTERN = Pattern.compile("::([0-9]{4,6})(?![0-9])");
    private static final Pattern CRS84_PATTERN = Pattern.compile("(?i)(?:^|[:/])CRS84$");

    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    public GeoJsonEpsgService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String detectar(Path arquivo) {
        if (arquivo == null || !arquivo.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".geojson")) {
            throw new IllegalArgumentException("A etapa de EPSG aceita somente arquivos .geojson.");
        }

        String tipo = null;
        boolean crsDeclarado = false;
        String nomeCrs = "";

        try (JsonParser parser = mapper.getFactory().createParser(arquivo.toFile())) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("GeoJSON inválido: esperado um FeatureCollection.");
            }

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String campo = parser.currentName();
                JsonToken valor = parser.nextToken();

                if ("type".equals(campo) && valor == JsonToken.VALUE_STRING) {
                    tipo = parser.getText();
                } else if ("crs".equals(campo) && valor != JsonToken.VALUE_NULL) {
                    crsDeclarado = true;
                    nomeCrs = extrairNomeCrs(mapper.readTree(parser));
                } else {
                    parser.skipChildren();
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Não foi possível ler o GeoJSON para verificar o EPSG.", e);
        }

        if (!"FeatureCollection".equals(tipo)) {
            throw new IllegalArgumentException("GeoJSON inválido: esperado um FeatureCollection.");
        }
        if (!crsDeclarado) {
            return EPSG_PADRAO_SEM_CRS;
        }

        String codigo = interpretarCrs(nomeCrs);
        if (codigo == null) {
            throw new IllegalArgumentException("O GeoJSON declara um CRS que não pôde ser interpretado: " + nomeCrs);
        }
        return codigo;
    }

    private String extrairNomeCrs(JsonNode crs) {
        String nome = crs.path("properties").path("name").asText("");
        if (nome.isBlank()) {
            nome = crs.path("name").asText("");
        }
        return nome;
    }

    // devolve só os números do código EPSG, ou null se não der pra interpretar
    static String interpretarCrs(String nome) {
        if (nome == null || nome.isBlank()) {
            return null;
        }
        String texto = nome.trim();
        Matcher matcher = EPSG_PATTERN.matcher(texto);
        if (matcher.find()) {
            return matcher.group(1);
        }
        if (CRS84_PATTERN.matcher(texto).find()) {
            return "4326";
        }
        Matcher fallback = DOIS_PONTOS_PATTERN.matcher(texto);
        if (fallback.find()) {
            return fallback.group(1);
        }
        return null;
    }

    public void validarCodigoEpsg(String epsg) {
        if (epsg == null || !epsg.matches("[1-9][0-9]{0,5}")) {
            throw new IllegalArgumentException("EPSG é obrigatório e deve ser um código numérico válido.");
        }
        Boolean existe = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM spatial_ref_sys WHERE srid = ?)",
                Boolean.class, Integer.valueOf(epsg));
        if (!Boolean.TRUE.equals(existe)) {
            throw new IllegalArgumentException("O código EPSG:" + epsg + " não existe na tabela spatial_ref_sys do PostGIS.");
        }
    }
}