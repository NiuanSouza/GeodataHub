package com.bifrostconnect.api_geo.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.bifrostconnect.api_geo.service.conversor.ConversorArquivoGeoespacial;

@Service
public class GeoJsonArquivoService {

    private final List<ConversorArquivoGeoespacial> conversores;

    public GeoJsonArquivoService(List<ConversorArquivoGeoespacial> conversores) {
        this.conversores = conversores;
    }

    /**
     * Prepara o arquivo para o pipeline geoespacial.
     * Identifica o conversor adequado para o formato (GeoJSON, Shapefile ZIP, GeoPackage, KML)
     * e retorna o caminho para um arquivo GeoJSON padronizado temporário ou original.
     */
    public Path preparar(Path arquivo) throws IOException {
        if (arquivo == null || !Files.isRegularFile(arquivo)) {
            throw new IllegalArgumentException("Arquivo não encontrado.");
        }

        String nome = arquivo.getFileName().toString().toLowerCase(Locale.ROOT);
        String extensao = "";
        int dotIndex = nome.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < nome.length() - 1) {
            extensao = nome.substring(dotIndex + 1);
        }

        for (ConversorArquivoGeoespacial conversor : conversores) {
            if (conversor.suporta(extensao, arquivo)) {
                return conversor.converterParaGeoJson(arquivo);
            }
        }

        throw new IllegalArgumentException(
                "Formato não suportado. Formatos aceitos: GeoJSON (.geojson, .json), Shapefile (.zip), CSV (.csv, .zip), GeoPackage (.gpkg) e KML (.kml, .kmz).");
    }

    public void excluirTemporario(Path arquivo, Path original) throws IOException {
        if (arquivo != null && original != null && !arquivo.equals(original)) {
            Files.deleteIfExists(arquivo);
        }
    }
}
