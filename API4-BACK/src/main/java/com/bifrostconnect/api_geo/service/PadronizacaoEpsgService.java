package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class PadronizacaoEpsgService {

    public static final String EPSG_OFICIAL = "4674";

    private static final String CRS_OFICIAL_URN = "urn:ogc:def:crs:EPSG::" + EPSG_OFICIAL;


    private static final int LOTE_MAX_FEICOES = 500;
    private static final long LOTE_MAX_CARACTERES = 5_000_000L;

    private static final String SQL_TRANSFORMAR = """
            SELECT ST_AsGeoJSON(
                       ST_Transform(ST_SetSRID(ST_GeomFromGeoJSON(t.g), ?), %s), 9, 0)
              FROM unnest(?::text[]) WITH ORDINALITY AS t(g, n)
             ORDER BY t.n
            """.formatted(EPSG_OFICIAL);

    private final ObjectMapper mapper = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    public PadronizacaoEpsgService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ResultadoChecagem(String declarado, String detectado) {
        public boolean divergente() {
            return !declarado.equals(detectado);
        }

        public boolean jaEstaNoPadraoOficial() {
            return EPSG_OFICIAL.equals(detectado);
        }
    }

    // aceita "4674", "EPSG:4674" ou "epsg:4674 " e devolve só o código ("4674")
    public String normalizarEpsg(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("EPSG é obrigatório e deve ser um código numérico válido.");
        }
        String limpo = valor.trim().toUpperCase(Locale.ROOT);
        if (limpo.startsWith("EPSG:")) {
            limpo = limpo.substring(5).trim();
        }
        return limpo;
    }

    public ResultadoChecagem comparar(String epsgDeclarado, String epsgDetectado) {
        return new ResultadoChecagem(normalizarEpsg(epsgDeclarado), normalizarEpsg(epsgDetectado));
    }


    public void converterParaEpsgOficial(Path origem, String epsgDetectado, Path destino) throws IOException {
        if (EPSG_OFICIAL.equals(epsgDetectado)) {
            Files.copy(origem, destino, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        int srid = Integer.parseInt(epsgDetectado);
        try {
            reprojetar(origem, destino, srid);
        } catch (IOException | RuntimeException e) {

            Files.deleteIfExists(destino);
            throw e;
        }
    }

    public String calcularSha256(Path arquivo) throws IOException {
        try (InputStream in = Files.newInputStream(arquivo)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                digest.update(buffer, 0, lidos);
            }
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }


    private void reprojetar(Path origem, Path destino, int srid) throws IOException {
        try (JsonParser parser = mapper.getFactory().createParser(origem.toFile());
             JsonGenerator gerador = mapper.getFactory().createGenerator(destino.toFile(), JsonEncoding.UTF8)) {

            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("GeoJSON inválido: esperado um FeatureCollection.");
            }

            gerador.writeStartObject();
            // O CRS do arquivo de origem é descartado; o da cópia é sempre o oficial.
            gerador.writeObjectFieldStart("crs");
            gerador.writeStringField("type", "name");
            gerador.writeObjectFieldStart("properties");
            gerador.writeStringField("name", CRS_OFICIAL_URN);
            gerador.writeEndObject();
            gerador.writeEndObject();

            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String campo = parser.currentName();
                JsonToken valor = parser.nextToken();

                if ("crs".equals(campo)) {
                    parser.skipChildren();
                } else if ("features".equals(campo) && valor == JsonToken.START_ARRAY) {
                    gerador.writeArrayFieldStart("features");
                    reprojetarFeicoes(parser, gerador, srid);
                    gerador.writeEndArray();
                } else {
                    gerador.writeFieldName(campo);
                    gerador.copyCurrentStructure(parser);
                }
            }
            gerador.writeEndObject();
        }
    }

    private void reprojetarFeicoes(JsonParser parser, JsonGenerator gerador, int srid) throws IOException {
        List<JsonNode> lote = new ArrayList<>();
        long caracteres = 0;

        while (parser.nextToken() != JsonToken.END_ARRAY) {
            JsonNode feicao = mapper.readTree(parser);
            lote.add(feicao);

            JsonNode geometria = feicao.get("geometry");
            if (geometria != null && geometria.isObject()) {
                caracteres += geometria.toString().length();
            }

            if (lote.size() >= LOTE_MAX_FEICOES || caracteres >= LOTE_MAX_CARACTERES) {
                descarregarLote(lote, gerador, srid);
                lote.clear();
                caracteres = 0;
            }
        }
        descarregarLote(lote, gerador, srid);
    }

    private void descarregarLote(List<JsonNode> lote, JsonGenerator gerador, int srid) throws IOException {
        List<ObjectNode> comGeometria = new ArrayList<>();
        List<String> geometrias = new ArrayList<>();

        for (JsonNode feicao : lote) {
            JsonNode geometria = feicao.get("geometry");
            if (feicao.isObject() && geometria != null && geometria.isObject()) {
                comGeometria.add((ObjectNode) feicao);
                geometrias.add(geometria.toString());
            }
        }

        List<String> convertidas = transformarNoPostgis(geometrias, srid);
        if (convertidas.size() != geometrias.size()) {
            throw new IllegalStateException("O PostGIS devolveu " + convertidas.size()
                    + " geometrias para " + geometrias.size() + " enviadas.");
        }
        for (int i = 0; i < comGeometria.size(); i++) {
            comGeometria.get(i).set("geometry", mapper.readTree(convertidas.get(i)));
        }

        // Feições sem geometria (geometry: null) fica igual de quando vem 
        for (JsonNode feicao : lote) {
            gerador.writeTree(feicao);
        }
    }

    private List<String> transformarNoPostgis(List<String> geometrias, int srid) {
        if (geometrias.isEmpty()) {
            return List.of();
        }
        // Tarefa 15: SRID que o PROJ não sabe projetar ou coordenada fora do domínio
        // estoura aqui; vira 422 e a carga vai pra Quarentena em vez de derrubar a requisição
        return ExecucaoGeoespacial.executar(jdbcTemplate,
                "conversão de coordenadas EPSG:" + srid + " -> EPSG:" + EPSG_OFICIAL,
                () -> jdbcTemplate.execute((ConnectionCallback<List<String>>) conexao -> {
                    Array array = conexao.createArrayOf("text", geometrias.toArray());
                    try (PreparedStatement ps = conexao.prepareStatement(SQL_TRANSFORMAR)) {
                        ps.setInt(1, srid);
                        ps.setArray(2, array);
                        List<String> resultado = new ArrayList<>(geometrias.size());
                        try (ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                resultado.add(rs.getString(1));
                            }
                        }
                        return resultado;
                    } finally {
                        array.free();
                    }
                }));
    }
}