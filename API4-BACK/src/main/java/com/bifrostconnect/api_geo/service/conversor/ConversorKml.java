package com.bifrostconnect.api_geo.service.conversor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

@Component
@Order(30)
public class ConversorKml implements ConversorArquivoGeoespacial {

    @Override
    public boolean suporta(String extensao, Path arquivo) {
        if ("kml".equalsIgnoreCase(extensao)) {
            return true;
        }
        if ("kmz".equalsIgnoreCase(extensao)) {
            return true;
        }

        if ("zip".equalsIgnoreCase(extensao) && arquivo != null && Files.isRegularFile(arquivo)) {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(arquivo))) {
                ZipEntry entrada;
                while ((entrada = zip.getNextEntry()) != null) {
                    if (entrada.getName().toLowerCase(Locale.ROOT).endsWith(".kml")) {
                        return true;
                    }
                    zip.closeEntry();
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public Path converterParaGeoJson(Path arquivoOrigem) throws IOException {
        String nome = arquivoOrigem.getFileName().toString().toLowerCase(Locale.ROOT);
        byte[] kmlBytes;

        if (nome.endsWith(".kmz") || nome.endsWith(".zip")) {
            kmlBytes = extrairKmlDeKmz(arquivoOrigem);
        } else {
            kmlBytes = Files.readAllBytes(arquivoOrigem);
        }

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            dbf.setNamespaceAware(false);
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(new ByteArrayInputStream(kmlBytes));

            List<FeatureKml> features = extrairFeatures(doc);
            if (features.isEmpty()) {
                throw new IllegalArgumentException("O arquivo KML não contém nenhum elemento <Placemark> válido.");
            }

            Path tempGeoJson = Files.createTempFile("kml-para-geojson-", ".geojson");
            escreverGeoJson(tempGeoJson, features);
            return tempGeoJson;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Falha ao analisar o arquivo KML: " + e.getMessage(), e);
        }
    }

    private byte[] extrairKmlDeKmz(Path arquivoKmz) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(arquivoKmz))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (!entrada.isDirectory() && entrada.getName().toLowerCase(Locale.ROOT).endsWith(".kml")) {
                    byte[] data = zip.readAllBytes();
                    zip.closeEntry();
                    return data;
                }
                zip.closeEntry();
            }
        }
        throw new IllegalArgumentException("O arquivo compactado não contém nenhum arquivo .kml interno.");
    }

    private List<FeatureKml> extrairFeatures(Document doc) {
        List<FeatureKml> lista = new ArrayList<>();
        NodeList placemarks = doc.getElementsByTagName("Placemark");

        for (int i = 0; i < placemarks.getLength(); i++) {
            Element pm = (Element) placemarks.item(i);
            Map<String, Object> props = new LinkedHashMap<>();

            String nome = getTagValue(pm, "name");
            if (nome != null) props.put("nome", nome);

            String desc = getTagValue(pm, "description");
            if (desc != null) props.put("descricao", desc);

            // ExtendedData -> Data name="..." -> value
            NodeList dataNodes = pm.getElementsByTagName("Data");
            for (int d = 0; d < dataNodes.getLength(); d++) {
                Element dataEl = (Element) dataNodes.item(d);
                String attrName = dataEl.getAttribute("name");
                String val = getTagValue(dataEl, "value");
                if (attrName != null && !attrName.isBlank() && val != null) {
                    props.put(attrName, val);
                }
            }

            // SimpleData name="..."
            NodeList simpleDataNodes = pm.getElementsByTagName("SimpleData");
            for (int s = 0; s < simpleDataNodes.getLength(); s++) {
                Element sEl = (Element) simpleDataNodes.item(s);
                String attrName = sEl.getAttribute("name");
                String val = sEl.getTextContent();
                if (attrName != null && !attrName.isBlank() && val != null) {
                    props.put(attrName, val.trim());
                }
            }

            // Geometria
            List<List<double[]>> poligonos = new ArrayList<>();
            NodeList polyNodes = pm.getElementsByTagName("Polygon");
            for (int p = 0; p < polyNodes.getLength(); p++) {
                Element polyEl = (Element) polyNodes.item(p);
                NodeList outerList = polyEl.getElementsByTagName("outerBoundaryIs");
                if (outerList.getLength() > 0) {
                    Element outer = (Element) outerList.item(0);
                    String coordsText = getTagValue(outer, "coordinates");
                    if (coordsText != null) {
                        List<double[]> ring = parseCoordinates(coordsText);
                        if (!ring.isEmpty()) {
                            poligonos.add(ring);
                        }
                    }
                }
            }

            if (!poligonos.isEmpty()) {
                lista.add(new FeatureKml("Polygon", poligonos, props));
            } else {
                NodeList pointNodes = pm.getElementsByTagName("Point");
                if (pointNodes.getLength() > 0) {
                    Element ptEl = (Element) pointNodes.item(0);
                    String coordsText = getTagValue(ptEl, "coordinates");
                    if (coordsText != null) {
                        List<double[]> pts = parseCoordinates(coordsText);
                        if (!pts.isEmpty()) {
                            lista.add(new FeatureKml("Point", List.of(pts), props));
                        }
                    }
                }
            }
        }

        return lista;
    }

    private List<double[]> parseCoordinates(String text) {
        List<double[]> list = new ArrayList<>();
        String[] tokens = text.trim().split("\\s+");
        for (String token : tokens) {
            String[] parts = token.split(",");
            if (parts.length >= 2) {
                try {
                    double lon = Double.parseDouble(parts[0].trim());
                    double lat = Double.parseDouble(parts[1].trim());
                    list.add(new double[]{lon, lat});
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!list.isEmpty()) {
            double[] first = list.get(0);
            double[] last = list.get(list.size() - 1);
            if (first[0] != last[0] || first[1] != last[1]) {
                list.add(new double[]{first[0], first[1]});
            }
        }
        return list;
    }

    private String getTagValue(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl.getLength() > 0) {
            Node n = nl.item(0);
            return n.getTextContent() != null ? n.getTextContent().trim() : null;
        }
        return null;
    }

    private void escreverGeoJson(Path destino, List<FeatureKml> features) throws IOException {
        JsonFactory jfactory = new JsonFactory();
        try (JsonGenerator jg = jfactory.createGenerator(destino.toFile(), JsonEncoding.UTF8)) {
            jg.writeStartObject();
            jg.writeStringField("type", "FeatureCollection");

            // KML é padronizado em WGS84 (EPSG:4326)
            jg.writeObjectFieldStart("crs");
            jg.writeStringField("type", "name");
            jg.writeObjectFieldStart("properties");
            jg.writeStringField("name", "urn:ogc:def:crs:EPSG::4326");
            jg.writeEndObject();
            jg.writeEndObject();

            jg.writeArrayFieldStart("features");

            for (FeatureKml f : features) {
                jg.writeStartObject();
                jg.writeStringField("type", "Feature");

                // Properties
                jg.writeObjectFieldStart("properties");
                for (Map.Entry<String, Object> entry : f.propriedades.entrySet()) {
                    jg.writeStringField(entry.getKey(), String.valueOf(entry.getValue()));
                }
                jg.writeEndObject();

                // Geometry
                jg.writeObjectFieldStart("geometry");
                if ("Polygon".equals(f.tipoGeom)) {
                    if (f.anéis.size() == 1) {
                        jg.writeStringField("type", "Polygon");
                        jg.writeArrayFieldStart("coordinates");
                        jg.writeStartArray();
                        for (double[] pt : f.anéis.get(0)) {
                            jg.writeStartArray();
                            jg.writeNumber(pt[0]);
                            jg.writeNumber(pt[1]);
                            jg.writeEndArray();
                        }
                        jg.writeEndArray();
                        jg.writeEndArray();
                    } else {
                        jg.writeStringField("type", "MultiPolygon");
                        jg.writeArrayFieldStart("coordinates");
                        for (List<double[]> ring : f.anéis) {
                            jg.writeStartArray();
                            jg.writeStartArray();
                            for (double[] pt : ring) {
                                jg.writeStartArray();
                                jg.writeNumber(pt[0]);
                                jg.writeNumber(pt[1]);
                                jg.writeEndArray();
                            }
                            jg.writeEndArray();
                            jg.writeEndArray();
                        }
                        jg.writeEndArray();
                    }
                } else if ("Point".equals(f.tipoGeom)) {
                    jg.writeStringField("type", "Point");
                    jg.writeArrayFieldStart("coordinates");
                    double[] pt = f.anéis.get(0).get(0);
                    jg.writeNumber(pt[0]);
                    jg.writeNumber(pt[1]);
                    jg.writeEndArray();
                }
                jg.writeEndObject(); // geometry

                jg.writeEndObject(); // Feature
            }

            jg.writeEndArray();
            jg.writeEndObject();
        }
    }

    private record FeatureKml(String tipoGeom, List<List<double[]>> anéis, Map<String, Object> propriedades) {}
}
