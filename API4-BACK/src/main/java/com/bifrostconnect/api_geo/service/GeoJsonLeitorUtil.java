package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeoJsonLeitorUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static List<String> extrairGeometriasWkt(Path caminhoArquivo) {
        List<String> listaWkt = new ArrayList<>();

        try {
            JsonNode rootNode = mapper.readTree(caminhoArquivo.toFile());
            JsonNode features = rootNode.get("features");

            if (features != null && features.isArray()) {
                for (JsonNode feature : features) {
                    JsonNode geometry = feature.get("geometry");
                    if (geometry != null) {
                        listaWkt.add(geometry.toString());
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Erro ao ler o arquivo GeoJSON para análise espacial: " + e.getMessage(), e);
        }

        return listaWkt;
    }
}