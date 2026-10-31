package com.bifrostconnect.api_geo.service.conversor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKBReader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

@Component
@Order(40)
public class ConversorGeoPackage implements ConversorArquivoGeoespacial {

    @Override
    public boolean suporta(String extensao, Path arquivo) {
        return "gpkg".equalsIgnoreCase(extensao);
    }

    @Override
    public Path converterParaGeoJson(Path arquivoOrigem) throws IOException {
        String url = "jdbc:sqlite:" + arquivoOrigem.toAbsolutePath();
        WKBReader wkbReader = new WKBReader();

        try (Connection conn = DriverManager.getConnection(url);
             Statement stmt = conn.createStatement()) {

            // 1. Descobrir tabela de feições e coluna de geometria
            String tableName = null;
            String geomCol = "geom";
            int srsId = 4674; // Padrão

            try (ResultSet rs = stmt.executeQuery("SELECT table_name, srs_id FROM gpkg_contents WHERE data_type = 'features' LIMIT 1")) {
                if (rs.next()) {
                    tableName = rs.getString("table_name");
                    int idSrs = rs.getInt("srs_id");
                    if (idSrs > 0) srsId = idSrs;
                }
            } catch (Exception ignored) {
            }

            if (tableName == null) {
                // Tenta tabela de geometria
                try (ResultSet rs = stmt.executeQuery("SELECT table_name, column_name, srs_id FROM gpkg_geometry_columns LIMIT 1")) {
                    if (rs.next()) {
                        tableName = rs.getString("table_name");
                        geomCol = rs.getString("column_name");
                        int idSrs = rs.getInt("srs_id");
                        if (idSrs > 0) srsId = idSrs;
                    }
                }
            } else {
                try (ResultSet rs = stmt.executeQuery("SELECT column_name FROM gpkg_geometry_columns WHERE table_name = '" + tableName + "' LIMIT 1")) {
                    if (rs.next()) {
                        geomCol = rs.getString("column_name");
                    }
                } catch (Exception ignored) {
                }
            }

            if (tableName == null) {
                throw new IllegalArgumentException("O arquivo GeoPackage (.gpkg) não contém camadas de feições vetoriais.");
            }

            // 2. Consulta feições e atributos
            List<Map<String, Object>> featureProps = new ArrayList<>();
            List<Geometry> geometries = new ArrayList<>();

            try (ResultSet rs = stmt.executeQuery("SELECT * FROM \"" + tableName + "\"")) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                while (rs.next()) {
                    Map<String, Object> props = new LinkedHashMap<>();
                    Geometry geomObj = null;

                    for (int i = 1; i <= colCount; i++) {
                        String colName = meta.getColumnName(i);
                        if (colName.equalsIgnoreCase(geomCol)) {
                            byte[] blob = rs.getBytes(i);
                            if (blob != null && blob.length > 8) {
                                geomObj = extrairGeometriaWkb(blob, wkbReader);
                            }
                        } else {
                            props.put(colName, rs.getObject(i));
                        }
                    }

                    if (geomObj != null) {
                        featureProps.add(props);
                        geometries.add(geomObj);
                    }
                }
            }

            Path geoJsonTemp = Files.createTempFile("gpkg-para-geojson-", ".geojson");
            escreverGeoJson(geoJsonTemp, String.valueOf(srsId), geometries, featureProps);
            return geoJsonTemp;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao converter arquivo GeoPackage (.gpkg): " + e.getMessage(), e);
        }
    }

    private Geometry extrairGeometriaWkb(byte[] gpkgBlob, WKBReader wkbReader) {
        if (gpkgBlob.length < 8) return null;
        // Magic 'GP' = 0x47, 0x50
        if (gpkgBlob[0] != 0x47 || gpkgBlob[1] != 0x50) {
            // Tenta como WKB puro
            try {
                return wkbReader.read(gpkgBlob);
            } catch (Exception e) {
                return null;
            }
        }

        byte flags = gpkgBlob[3];
        int envelopeCode = (flags & 0x0E) >> 1;
        int envelopeBytes = switch (envelopeCode) {
            case 1 -> 32;
            case 2, 3 -> 48;
            case 4 -> 64;
            default -> 0;
        };

        int offset = 8 + envelopeBytes;
        if (offset >= gpkgBlob.length) return null;

        byte[] wkb = new byte[gpkgBlob.length - offset];
        System.arraycopy(gpkgBlob, offset, wkb, 0, wkb.length);

        try {
            return wkbReader.read(wkb);
        } catch (Exception e) {
            return null;
        }
    }

    private void escreverGeoJson(Path destino, String epsg, List<Geometry> geometries, List<Map<String, Object>> atributos) throws IOException {
        JsonFactory jfactory = new JsonFactory();
        try (JsonGenerator jg = jfactory.createGenerator(destino.toFile(), JsonEncoding.UTF8)) {
            jg.writeStartObject();
            jg.writeStringField("type", "FeatureCollection");

            if (epsg != null && !epsg.isBlank()) {
                jg.writeObjectFieldStart("crs");
                jg.writeStringField("type", "name");
                jg.writeObjectFieldStart("properties");
                jg.writeStringField("name", "urn:ogc:def:crs:EPSG::" + epsg);
                jg.writeEndObject();
                jg.writeEndObject();
            }

            jg.writeArrayFieldStart("features");

            for (int i = 0; i < geometries.size(); i++) {
                Geometry g = geometries.get(i);
                Map<String, Object> props = i < atributos.size() ? atributos.get(i) : Map.of();

                jg.writeStartObject();
                jg.writeStringField("type", "Feature");

                // Properties
                jg.writeObjectFieldStart("properties");
                for (Map.Entry<String, Object> entry : props.entrySet()) {
                    if (entry.getValue() == null) {
                        jg.writeNullField(entry.getKey());
                    } else if (entry.getValue() instanceof Number n) {
                        if (n instanceof Double || n instanceof Float) {
                            jg.writeNumberField(entry.getKey(), n.doubleValue());
                        } else {
                            jg.writeNumberField(entry.getKey(), n.longValue());
                        }
                    } else if (entry.getValue() instanceof Boolean b) {
                        jg.writeBooleanField(entry.getKey(), b);
                    } else {
                        jg.writeStringField(entry.getKey(), entry.getValue().toString());
                    }
                }
                jg.writeEndObject();

                // Geometry
                jg.writeObjectFieldStart("geometry");
                escreverGeometriaJts(jg, g);
                jg.writeEndObject();

                jg.writeEndObject();
            }

            jg.writeEndArray();
            jg.writeEndObject();
        }
    }

    private void escreverGeometriaJts(JsonGenerator jg, Geometry geom) throws IOException {
        if (geom instanceof Polygon poly) {
            jg.writeStringField("type", "Polygon");
            jg.writeArrayFieldStart("coordinates");
            escreverRing(jg, poly.getExteriorRing());
            for (int h = 0; h < poly.getNumInteriorRing(); h++) {
                escreverRing(jg, poly.getInteriorRingN(h));
            }
            jg.writeEndArray();
        } else if (geom instanceof MultiPolygon mp) {
            jg.writeStringField("type", "MultiPolygon");
            jg.writeArrayFieldStart("coordinates");
            for (int p = 0; p < mp.getNumGeometries(); p++) {
                Polygon polyInMp = (Polygon) mp.getGeometryN(p);
                jg.writeStartArray();
                escreverRing(jg, polyInMp.getExteriorRing());
                for (int h = 0; h < polyInMp.getNumInteriorRing(); h++) {
                    escreverRing(jg, polyInMp.getInteriorRingN(h));
                }
                jg.writeEndArray();
            }
            jg.writeEndArray();
        } else if (geom instanceof Point pt) {
            jg.writeStringField("type", "Point");
            jg.writeArrayFieldStart("coordinates");
            jg.writeNumber(pt.getX());
            jg.writeNumber(pt.getY());
            jg.writeEndArray();
        } else {
            jg.writeStringField("type", "GeometryCollection");
            jg.writeArrayFieldStart("geometries");
            jg.writeEndArray();
        }
    }

    private void escreverRing(JsonGenerator jg, LineString ring) throws IOException {
        jg.writeStartArray();
        Coordinate[] coords = ring.getCoordinates();
        for (Coordinate c : coords) {
            jg.writeStartArray();
            jg.writeNumber(c.x);
            jg.writeNumber(c.y);
            jg.writeEndArray();
        }
        jg.writeEndArray();
    }
}
