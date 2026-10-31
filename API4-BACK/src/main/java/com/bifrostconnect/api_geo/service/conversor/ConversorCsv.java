package com.bifrostconnect.api_geo.service.conversor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.WKTReader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

@Component
@Order(25)
public class ConversorCsv implements ConversorArquivoGeoespacial {

    private static final long LIMITE_DESCOMPACTACAO = 5L * 1024 * 1024 * 1024; // 5 GB
    private static final int IO_BUFFER_SIZE = 64 * 1024;
    private static final double DELTA_PONTO_GRAUS = 0.0001; // ~11m para representar pontos como micro-polígonos

    private static final Map<String, double[]> CENTROIDES_UF = Map.ofEntries(
            Map.entry("AC", new double[]{-70.55, -9.02}),
            Map.entry("AL", new double[]{-36.62, -9.57}),
            Map.entry("AM", new double[]{-64.63, -4.13}),
            Map.entry("AP", new double[]{-51.77, 1.39}),
            Map.entry("BA", new double[]{-41.71, -12.52}),
            Map.entry("CE", new double[]{-39.32, -5.20}),
            Map.entry("DF", new double[]{-47.93, -15.78}),
            Map.entry("ES", new double[]{-40.31, -19.57}),
            Map.entry("GO", new double[]{-49.62, -16.04}),
            Map.entry("MA", new double[]{-45.27, -5.06}),
            Map.entry("MG", new double[]{-44.55, -18.51}),
            Map.entry("MS", new double[]{-54.84, -20.51}),
            Map.entry("MT", new double[]{-55.91, -12.64}),
            Map.entry("PA", new double[]{-52.48, -3.79}),
            Map.entry("PB", new double[]{-36.78, -7.12}),
            Map.entry("PE", new double[]{-37.99, -8.38}),
            Map.entry("PI", new double[]{-42.80, -7.42}),
            Map.entry("PR", new double[]{-51.65, -24.65}),
            Map.entry("RJ", new double[]{-42.66, -22.25}),
            Map.entry("RN", new double[]{-36.67, -5.81}),
            Map.entry("RO", new double[]{-62.82, -10.83}),
            Map.entry("RR", new double[]{-61.39, 2.05}),
            Map.entry("RS", new double[]{-53.39, -29.75}),
            Map.entry("SC", new double[]{-50.45, -27.24}),
            Map.entry("SE", new double[]{-37.38, -10.57}),
            Map.entry("SP", new double[]{-48.63, -22.27}),
            Map.entry("TO", new double[]{-48.33, -10.18})
    );

    @Override
    public boolean suporta(String extensao, Path arquivo) {
        if ("csv".equalsIgnoreCase(extensao)) {
            return true;
        }
        if ("zip".equalsIgnoreCase(extensao) && arquivo != null && Files.isRegularFile(arquivo)) {
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(arquivo)))) {
                ZipEntry entrada;
                boolean temCsv = false;
                boolean temShpOuGeoJson = false;
                while ((entrada = zip.getNextEntry()) != null) {
                    String nome = entrada.getName().toLowerCase(Locale.ROOT);
                    if (!entrada.isDirectory()) {
                        if (nome.endsWith(".csv")) {
                            temCsv = true;
                        } else if (nome.endsWith(".shp") || nome.endsWith(".geojson") || nome.endsWith(".json")) {
                            temShpOuGeoJson = true;
                        }
                    }
                    zip.closeEntry();
                }
                return temCsv && !temShpOuGeoJson;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Override
    public Path converterParaGeoJson(Path arquivoOrigem) throws IOException {
        String nome = arquivoOrigem.getFileName().toString().toLowerCase(Locale.ROOT);
        Path csvTemp = null;
        try {
            Path csvAlvo;
            if (nome.endsWith(".zip")) {
                csvTemp = extrairCsvDoZip(arquivoOrigem);
                csvAlvo = csvTemp;
            } else {
                csvAlvo = arquivoOrigem;
            }

            Charset charset = detectarCharset(csvAlvo);
            Path geoJsonSaida = Files.createTempFile("csv-para-geojson-", ".geojson");
            converterCsvEmStreaming(csvAlvo, charset, geoJsonSaida);
            return geoJsonSaida;
        } finally {
            if (csvTemp != null) {
                Files.deleteIfExists(csvTemp);
            }
        }
    }

    private Path extrairCsvDoZip(Path arquivoZip) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(arquivoZip), IO_BUFFER_SIZE))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (!entrada.isDirectory() && entrada.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                    Path extraido = Files.createTempFile("csv-zip-extraido-", ".csv");
                    try (var out = new BufferedOutputStream(Files.newOutputStream(extraido), IO_BUFFER_SIZE)) {
                        byte[] buf = new byte[IO_BUFFER_SIZE];
                        long total = 0;
                        int lidos;
                        while ((lidos = zip.read(buf)) != -1) {
                            total += lidos;
                            if (total > LIMITE_DESCOMPACTACAO) {
                                Files.deleteIfExists(extraido);
                                throw new IllegalArgumentException("O CSV extraído do ZIP excede o limite de 5 GB.");
                            }
                            out.write(buf, 0, lidos);
                        }
                    }
                    return extraido;
                }
                zip.closeEntry();
            }
        }
        throw new IllegalArgumentException("Nenhum arquivo .csv encontrado dentro do ZIP.");
    }

    private Charset detectarCharset(Path csvFile) throws IOException {
        byte[] sample = new byte[8192];
        int lidos;
        try (InputStream in = Files.newInputStream(csvFile)) {
            lidos = in.read(sample);
        }
        if (lidos <= 0) {
            return StandardCharsets.UTF_8;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(sample, 0, lidos));
            return StandardCharsets.UTF_8;
        } catch (CharacterCodingException e) {
            return StandardCharsets.ISO_8859_1;
        }
    }

    private void converterCsvEmStreaming(Path csvFile, Charset charset, Path geoJsonDestino) throws IOException {
        JsonFactory jfactory = new JsonFactory();
        WKTReader wktReader = new WKTReader();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(csvFile), charset), IO_BUFFER_SIZE);
             JsonGenerator jg = jfactory.createGenerator(
                     new BufferedOutputStream(Files.newOutputStream(geoJsonDestino), IO_BUFFER_SIZE),
                     JsonEncoding.UTF8)) {

            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("O arquivo CSV está vazio ou sem cabeçalho.");
            }
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            char separador = detectarSeparador(headerLine);
            List<String> colunas = parseLinhaCsv(headerLine, separador);

            int idxLat = -1;
            int idxLon = -1;
            int idxWkt = -1;
            int idxUf = -1;
            int idxId = -1;

            for (int i = 0; i < colunas.size(); i++) {
                String c = normalizarNomeColuna(colunas.get(i));
                if (idxLat < 0 && (c.equals("latitude") || c.equals("lat") || c.equals("nr_latitude") || c.equals("coord_y"))) {
                    idxLat = i;
                } else if (idxLon < 0 && (c.equals("longitude") || c.equals("lon") || c.equals("long") || c.equals("lng") || c.equals("nr_longitude") || c.equals("coord_x"))) {
                    idxLon = i;
                } else if (idxWkt < 0 && (c.equals("wkt") || c.equals("geom") || c.equals("geometria") || c.equals("the_geom"))) {
                    idxWkt = i;
                } else if (idxUf < 0 && (c.equals("uf") || c.equals("estado") || c.equals("sigla_uf"))) {
                    idxUf = i;
                }
                if (idxId < 0 && (c.equals("cod_imovel") || c.equals("codigo_imovel") || c.equals("codigo_uc") || c.equals("id_uc") || c.equals("cnuc") || c.equals("id"))) {
                    idxId = i;
                }
            }

            jg.writeStartObject();
            jg.writeStringField("type", "FeatureCollection");
            jg.writeObjectFieldStart("crs");
            jg.writeStringField("type", "name");
            jg.writeObjectFieldStart("properties");
            jg.writeStringField("name", "urn:ogc:def:crs:EPSG::4674");
            jg.writeEndObject();
            jg.writeEndObject();

            jg.writeArrayFieldStart("features");

            String linha;
            int numLinha = 0;
            while ((linha = reader.readLine()) != null) {
                if (linha.isBlank()) {
                    continue;
                }
                numLinha++;
                List<String> valores = parseLinhaCsv(linha, separador);

                Geometry wktGeom = null;
                double lon = Double.NaN;
                double lat = Double.NaN;

                if (idxWkt >= 0 && idxWkt < valores.size()) {
                    String wktStr = valores.get(idxWkt).trim();
                    if (!wktStr.isEmpty()) {
                        try {
                            wktGeom = wktReader.read(wktStr);
                        } catch (Exception ignored) {
                        }
                    }
                }

                if (wktGeom == null && idxLat >= 0 && idxLon >= 0 && idxLat < valores.size() && idxLon < valores.size()) {
                    lat = parseDoubleSeguro(valores.get(idxLat));
                    lon = parseDoubleSeguro(valores.get(idxLon));
                }

                if (wktGeom == null && (Double.isNaN(lat) || Double.isNaN(lon))) {
                    double[] centro = obterCentroUf(idxUf >= 0 && idxUf < valores.size() ? valores.get(idxUf) : null, numLinha);
                    lon = centro[0];
                    lat = centro[1];
                }

                if (wktGeom == null && (lat < -90 || lat > 90 || lon < -180 || lon > 180)) {
                    continue;
                }

                jg.writeStartObject();
                jg.writeStringField("type", "Feature");

                jg.writeObjectFieldStart("properties");
                boolean escreveuCodImovel = false;
                for (int i = 0; i < colunas.size(); i++) {
                    String chave = colunas.get(i).trim();
                    if (chave.isEmpty()) {
                        chave = "col_" + i;
                    }
                    String val = i < valores.size() ? valores.get(i).trim() : "";
                    if (val.isEmpty()) {
                        jg.writeNullField(chave);
                    } else {
                        jg.writeStringField(chave, val);
                    }
                    if ("cod_imovel".equalsIgnoreCase(chave) && !val.isEmpty()) {
                        escreveuCodImovel = true;
                    }
                }
                if (!escreveuCodImovel) {
                    String idGerado = (idxId >= 0 && idxId < valores.size() && !valores.get(idxId).isBlank())
                            ? valores.get(idxId).trim() + "_" + numLinha
                            : "CSV_REG_" + numLinha;
                    jg.writeStringField("cod_imovel", idGerado);
                }
                jg.writeEndObject();

                jg.writeObjectFieldStart("geometry");
                if (wktGeom instanceof Polygon poly) {
                    escreverPoligonoJts(jg, poly);
                } else if (wktGeom instanceof MultiPolygon mp && mp.getNumGeometries() > 0 && mp.getGeometryN(0) instanceof Polygon poly) {
                    escreverPoligonoJts(jg, poly);
                } else if (wktGeom instanceof Point pt) {
                    escreverMicroPoligono(jg, pt.getX(), pt.getY());
                } else {
                    escreverMicroPoligono(jg, lon, lat);
                }
                jg.writeEndObject();

                jg.writeEndObject();
            }

            jg.writeEndArray();
            jg.writeEndObject();
        }
    }

    private void escreverPoligonoJts(JsonGenerator jg, Polygon poly) throws IOException {
        jg.writeStringField("type", "Polygon");
        jg.writeArrayFieldStart("coordinates");
        jg.writeStartArray();
        Coordinate[] coords = poly.getExteriorRing().getCoordinates();
        for (Coordinate c : coords) {
            jg.writeStartArray();
            jg.writeNumber(c.x);
            jg.writeNumber(c.y);
            jg.writeEndArray();
        }
        jg.writeEndArray();
        jg.writeEndArray();
    }

    private void escreverMicroPoligono(JsonGenerator jg, double lon, double lat) throws IOException {
        double minLon = Math.max(-179.999, lon - DELTA_PONTO_GRAUS);
        double maxLon = Math.min(179.999, lon + DELTA_PONTO_GRAUS);
        double minLat = Math.max(-89.999, lat - DELTA_PONTO_GRAUS);
        double maxLat = Math.min(89.999, lat + DELTA_PONTO_GRAUS);

        jg.writeStringField("type", "Polygon");
        jg.writeArrayFieldStart("coordinates");
        jg.writeStartArray();
        escreverPar(jg, minLon, minLat);
        escreverPar(jg, maxLon, minLat);
        escreverPar(jg, maxLon, maxLat);
        escreverPar(jg, minLon, maxLat);
        escreverPar(jg, minLon, minLat);
        jg.writeEndArray();
        jg.writeEndArray();
    }

    private void escreverPar(JsonGenerator jg, double x, double y) throws IOException {
        jg.writeStartArray();
        jg.writeNumber(x);
        jg.writeNumber(y);
        jg.writeEndArray();
    }

    private double[] obterCentroUf(String ufRaw, int indice) {
        double[] base = new double[]{-47.93, -15.78};
        if (ufRaw != null && !ufRaw.isBlank()) {
            String sigla = ufRaw.trim().toUpperCase(Locale.ROOT);
            if (sigla.length() >= 2) {
                sigla = sigla.substring(0, 2);
            }
            if (CENTROIDES_UF.containsKey(sigla)) {
                base = CENTROIDES_UF.get(sigla);
            }
        }
        double offsetLon = ((indice % 100) * 0.0005);
        double offsetLat = (((indice / 100) % 100) * 0.0005);
        return new double[]{base[0] + offsetLon, base[1] + offsetLat};
    }

    private static double parseDoubleSeguro(String valor) {
        if (valor == null || valor.isBlank()) {
            return Double.NaN;
        }
        try {
            return Double.parseDouble(valor.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String normalizarNomeColuna(String col) {
        if (col == null) return "";
        return col.trim().toLowerCase(Locale.ROOT)
                .replace("ó", "o")
                .replace("á", "a")
                .replace("ã", "a")
                .replace("ç", "c")
                .replace("é", "e")
                .replace("í", "i")
                .replace("ú", "u")
                .replace(" ", "_");
    }

    private static char detectarSeparador(String header) {
        int pontoVirgula = 0;
        int virgula = 0;
        boolean emAspas = false;
        for (int i = 0; i < header.length(); i++) {
            char c = header.charAt(i);
            if (c == '"') {
                emAspas = !emAspas;
            } else if (!emAspas) {
                if (c == ';') pontoVirgula++;
                else if (c == ',') virgula++;
            }
        }
        return pontoVirgula >= virgula && pontoVirgula > 0 ? ';' : ',';
    }

    static List<String> parseLinhaCsv(String linha, char separador) {
        List<String> campos = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean emAspas = false;

        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (emAspas && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    emAspas = !emAspas;
                }
            } else if (c == separador && !emAspas) {
                campos.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        campos.add(sb.toString());
        return campos;
    }
}
