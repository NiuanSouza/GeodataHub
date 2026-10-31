package com.bifrostconnect.api_geo.service.conversor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.bifrostconnect.api_geo.service.GeoJsonArquivoService;

class ConversorMultiFormatoTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("ConversorGeoJson: deve aceitar .geojson diretamente")
    void deveAceitarGeoJsonDireto() throws Exception {
        Path geojson = tempDir.resolve("teste.geojson");
        Files.writeString(geojson, "{\"type\":\"FeatureCollection\",\"features\":[]}");

        ConversorGeoJson conversor = new ConversorGeoJson();
        assertTrue(conversor.suporta("geojson", geojson));

        Path resultado = conversor.converterParaGeoJson(geojson);
        assertEquals(geojson, resultado);
    }

    @Test
    @DisplayName("ConversorGeoJson: deve extrair GeoJSON contido em ZIP")
    void deveExtrairGeoJsonDeZip() throws Exception {
        Path zipFile = tempDir.resolve("teste_geojson.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile.toFile()))) {
            zos.putNextEntry(new ZipEntry("dados.geojson"));
            zos.write("{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes());
            zos.closeEntry();
        }

        ConversorGeoJson conversor = new ConversorGeoJson();
        assertTrue(conversor.suporta("zip", zipFile));

        Path resultado = conversor.converterParaGeoJson(zipFile);
        assertNotNull(resultado);
        assertTrue(Files.exists(resultado));
        assertTrue(Files.readString(resultado).contains("FeatureCollection"));

        Files.deleteIfExists(resultado);
    }

    @Test
    @DisplayName("ConversorKml: deve converter KML com Placemark e Polygon para GeoJSON")
    void deveConverterKmlParaGeoJson() throws Exception {
        Path kmlFile = tempDir.resolve("imovel.kml");
        String kmlConteudo = """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                  <Document>
                    <Placemark>
                      <name>Fazenda Boa Esperança</name>
                      <description>Propriedade Teste</description>
                      <ExtendedData>
                        <Data name="cod_imovel">
                          <value>GO-12345</value>
                        </Data>
                      </ExtendedData>
                      <Polygon>
                        <outerBoundaryIs>
                          <LinearRing>
                            <coordinates>
                              -49.25,-16.68,0 -49.24,-16.68,0 -49.24,-16.69,0 -49.25,-16.69,0 -49.25,-16.68,0
                            </coordinates>
                          </LinearRing>
                        </outerBoundaryIs>
                      </Polygon>
                    </Placemark>
                  </Document>
                </kml>
                """;
        Files.writeString(kmlFile, kmlConteudo);

        ConversorKml conversor = new ConversorKml();
        assertTrue(conversor.suporta("kml", kmlFile));

        Path resultado = conversor.converterParaGeoJson(kmlFile);
        assertNotNull(resultado);
        assertTrue(Files.exists(resultado));

        String geoJsonStr = Files.readString(resultado);
        assertTrue(geoJsonStr.contains("Fazenda Boa Esperança"));
        assertTrue(geoJsonStr.contains("GO-12345"));
        assertTrue(geoJsonStr.contains("EPSG::4326"));
        assertTrue(geoJsonStr.contains("Polygon"));

        Files.deleteIfExists(resultado);
    }

    @Test
    @DisplayName("ConversorCsv: deve converter CSV com Latitude/Longitude e CSV em ZIP para GeoJSON")
    void deveConverterCsvEZipComCsvParaGeoJson() throws Exception {
        Path csvFile = tempDir.resolve("queimadas.csv");
        String csvConteudo = """
                DataHora,Satelite,Estado,Municipio,Bioma,Latitude,Longitude
                2026/08/16 16:42:00,AQUA_M-T,GOIAS,GOIANIA,Cerrado,-16.68,-49.25
                """;
        Files.writeString(csvFile, csvConteudo);

        ConversorCsv conversor = new ConversorCsv();
        assertTrue(conversor.suporta("csv", csvFile));

        Path resultadoCsv = conversor.converterParaGeoJson(csvFile);
        assertNotNull(resultadoCsv);
        assertTrue(Files.exists(resultadoCsv));
        String geoJsonCsv = Files.readString(resultadoCsv);
        assertTrue(geoJsonCsv.contains("FeatureCollection"));
        assertTrue(geoJsonCsv.contains("GOIANIA"));
        assertTrue(geoJsonCsv.contains("Polygon"));
        Files.deleteIfExists(resultadoCsv);

        Path zipCsvFile = tempDir.resolve("bdqueimadas.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipCsvFile.toFile()))) {
            zos.putNextEntry(new ZipEntry("focos.csv"));
            zos.write(csvConteudo.getBytes());
            zos.closeEntry();
        }
        assertTrue(conversor.suporta("zip", zipCsvFile));
        Path resultadoZipCsv = conversor.converterParaGeoJson(zipCsvFile);
        assertNotNull(resultadoZipCsv);
        assertTrue(Files.exists(resultadoZipCsv));
        assertTrue(Files.readString(resultadoZipCsv).contains("AQUA_M-T"));
        Files.deleteIfExists(resultadoZipCsv);
    }

    @Test
    @DisplayName("GeoJsonArquivoService: delega para o conversor apropriado na lista")
    void servicoDelegaParaConversorApropriado() throws Exception {
        ConversorGeoJson cGeoJson = new ConversorGeoJson();
        ConversorKml cKml = new ConversorKml();
        ConversorShapefileZip cShp = new ConversorShapefileZip();
        ConversorCsv cCsv = new ConversorCsv();
        ConversorGeoPackage cGpkg = new ConversorGeoPackage();

        GeoJsonArquivoService service = new GeoJsonArquivoService(List.of(cGeoJson, cKml, cShp, cCsv, cGpkg));

        Path geojson = tempDir.resolve("mapa.geojson");
        Files.writeString(geojson, "{\"type\":\"FeatureCollection\",\"features\":[]}");

        Path res = service.preparar(geojson);
        assertEquals(geojson, res);

        Path txtInvalido = tempDir.resolve("invalido.txt");
        Files.writeString(txtInvalido, "texto comum");

        assertThrows(IllegalArgumentException.class, () -> service.preparar(txtInvalido));
    }
}
