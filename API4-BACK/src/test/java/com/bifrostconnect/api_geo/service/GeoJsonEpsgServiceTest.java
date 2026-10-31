package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class GeoJsonEpsgServiceTest {
    @TempDir Path tempDir;

    private String detectar(String nomeArquivo, String conteudo) throws Exception {
        Path arquivo = tempDir.resolve(nomeArquivo);
        Files.writeString(arquivo, conteudo);
        return new GeoJsonEpsgService(mock(JdbcTemplate.class)).detectar(arquivo);
    }

    @Test
    void usa4326QuandoGeoJsonNaoDeclaraCrs() throws Exception {
        Path arquivo = tempDir.resolve("sem-crs.geojson");
        Files.writeString(arquivo, """
                {"type":"FeatureCollection","features":[]}
                """);
        GeoJsonEpsgService service = new GeoJsonEpsgService(mock(JdbcTemplate.class));
        assertEquals("4326", service.detectar(arquivo));
    }

    @Test
    void leEpsgDosMetadadosAntigosDoGeoJson() throws Exception {
        Path arquivo = tempDir.resolve("epsg-31983.geojson");
        Files.writeString(arquivo, """
                {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::31983"}},"features":[]}
                """);
        GeoJsonEpsgService service = new GeoJsonEpsgService(mock(JdbcTemplate.class));
        assertEquals("31983", service.detectar(arquivo));
    }

    @Test
    void rejeitaArquivoQueNaoSejaGeoJson() throws Exception {
        Path arquivo = tempDir.resolve("arquivo.zip");
        Files.writeString(arquivo, "x");
        GeoJsonEpsgService service = new GeoJsonEpsgService(mock(JdbcTemplate.class));
        assertThrows(IllegalArgumentException.class, () -> service.detectar(arquivo));
    }


    @Test
    void reconheceCrs84ComoEpsg4326() throws Exception {
        // É o que o QGIS/GDAL gravam para WGS 84 lon/lat.
        assertEquals("4326", detectar("crs84.geojson", """
                {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:OGC:1.3:CRS84"}},"features":[]}
                """));
    }

    @Test
    void leEpsgSimplesEComVersaoNaUrn() throws Exception {
        assertEquals("4674", detectar("simples.geojson", """
                {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"EPSG:4674"}},"features":[]}
                """));
        assertEquals("4326", detectar("versao.geojson", """
                {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG:6.6:4326"}},"features":[]}
                """));
        assertEquals("4674", detectar("url.geojson", """
                {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"http://www.opengis.net/def/crs/EPSG/0/4674"}},"features":[]}
                """));
    }

    @Test
    void encontraCrsMesmoDepoisDasFeicoes() throws Exception {
        assertEquals("31983", detectar("crs-no-fim.geojson", """
                {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"crs":"EPSG:1111"},"geometry":null}],
                 "crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::31983"}}}
                """));
    }

    @Test
    void crsNuloEquivaleASemCrs() throws Exception {
        assertEquals("4326", detectar("crs-nulo.geojson", """
                {"type":"FeatureCollection","crs":null,"features":[]}
                """));
    }

    @Test
    void rejeitaCrsQueNaoPodeSerInterpretado() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> detectar("crs-estranho.geojson", """
                {"type":"FeatureCollection","crs":{"type":"name","properties":{"name":"sistema-local-da-fazenda"}},"features":[]}
                """));
    }

    @Test
    void rejeitaJsonQueNaoEFeatureCollection() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> detectar("feature.geojson", """
                {"type":"Feature","properties":{},"geometry":null}
                """));
        assertThrows(IllegalArgumentException.class, () -> detectar("array.geojson", "[1,2,3]"));
    }

    @Test
    void rejeitaJsonCorrompido() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> detectar("corrompido.geojson", "Conteudo invalido para geojson"));
        assertThrows(IllegalArgumentException.class, () -> detectar("truncado.geojson", "{\"type\":\"FeatureCollection\",\"features\":["));
    }
}