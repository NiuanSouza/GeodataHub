package com.bifrostconnect.api_geo.service.conversor;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

@Component
@Order(20)
public class ConversorShapefileZip implements ConversorArquivoGeoespacial {

    private static final long LIMITE_DESCOMPACTACAO = 5L * 1024 * 1024 * 1024; // 5 GB
    private static final int IO_BUFFER_SIZE = 64 * 1024; // 64 KB buffer para alto throughput

    @Override
    public boolean suporta(String extensao, Path arquivo) {
        if (!"zip".equalsIgnoreCase(extensao) || arquivo == null || !Files.isRegularFile(arquivo)) {
            return false;
        }

        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(arquivo)))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                String nome = entrada.getName().toLowerCase(Locale.ROOT);
                if (!entrada.isDirectory() && nome.endsWith(".shp")) {
                    return true;
                }
                zip.closeEntry();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public Path converterParaGeoJson(Path arquivoOrigem) throws IOException {
        Path tempDir = Files.createTempDirectory("shp-extraido-");
        try {
            descompactarZip(arquivoOrigem, tempDir);

            Path shpPath;
            try (var stream = Files.walk(tempDir)) {
                shpPath = stream
                        .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".shp"))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Nenhum arquivo .shp encontrado dentro do arquivo ZIP."));
            }

            String baseName = shpPath.getFileName().toString();
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
            Path parentDir = shpPath.getParent();

            Path dbfPath = localizarArquivoComplementar(parentDir, baseName, ".dbf");
            Path prjPath = localizarArquivoComplementar(parentDir, baseName, ".prj", ".prj.txt");

            String epsg = extrairEpsgDePrj(prjPath);

            Path geoJsonSaida = Files.createTempFile("shp-para-geojson-", ".geojson");
            converterEmStreaming(shpPath, dbfPath, epsg, geoJsonSaida);

            return geoJsonSaida;

        } finally {
            removerDiretorio(tempDir);
        }
    }

    private Path localizarArquivoComplementar(Path parentDir, String baseName, String... extensoes) {
        for (String ext : extensoes) {
            Path pLower = parentDir.resolve(baseName + ext.toLowerCase(Locale.ROOT));
            if (Files.exists(pLower)) return pLower;
            Path pUpper = parentDir.resolve(baseName + ext.toUpperCase(Locale.ROOT));
            if (Files.exists(pUpper)) return pUpper;
        }
        try (var stream = Files.list(parentDir)) {
            return stream.filter(p -> {
                String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                for (String ext : extensoes) {
                    if (n.endsWith(ext.toLowerCase(Locale.ROOT))) return true;
                }
                return false;
            }).findFirst().orElse(null);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void descompactarZip(Path zipFile, Path destino) throws IOException {
        long totalBytes = 0;
        byte[] buffer = new byte[IO_BUFFER_SIZE];

        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(zipFile), IO_BUFFER_SIZE))) {
            ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                Path destinoArquivo = destino.resolve(entrada.getName()).normalize();
                if (!destinoArquivo.startsWith(destino)) {
                    throw new SecurityException("Entrada ZIP inválida (Zip Slip): " + entrada.getName());
                }

                if (entrada.isDirectory()) {
                    Files.createDirectories(destinoArquivo);
                } else {
                    if (destinoArquivo.getParent() != null) {
                        Files.createDirectories(destinoArquivo.getParent());
                    }
                    try (var out = new BufferedOutputStream(Files.newOutputStream(destinoArquivo), IO_BUFFER_SIZE)) {
                        int lidos;
                        while ((lidos = zip.read(buffer)) != -1) {
                            totalBytes += lidos;
                            if (totalBytes > LIMITE_DESCOMPACTACAO) {
                                throw new IllegalArgumentException("O conteúdo do ZIP excede o limite máximo permitido de 5 GB.");
                            }
                            out.write(buffer, 0, lidos);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private String extrairEpsgDePrj(Path prjPath) {
        if (prjPath == null || !Files.exists(prjPath)) {
            return "4674"; // Default SIRGAS 2000
        }
        try {
            String wkt = Files.readString(prjPath, StandardCharsets.ISO_8859_1).toUpperCase(Locale.ROOT);
            Pattern p = Pattern.compile("AUTHORITY\\[\"EPSG\"\\s*,\\s*\"?(\\d+)\"?\\]");
            Matcher m = p.matcher(wkt);
            String ultimoEpsg = null;
            while (m.find()) {
                ultimoEpsg = m.group(1);
            }
            if (ultimoEpsg != null) {
                return ultimoEpsg;
            }
            if (wkt.contains("SIRGAS") || wkt.contains("4674")) return "4674";
            if (wkt.contains("WGS_1984") || wkt.contains("WGS 84") || wkt.contains("4326")) return "4326";
            if (wkt.contains("31982")) return "31982";
            if (wkt.contains("31983")) return "31983";
            if (wkt.contains("5880")) return "5880";
        } catch (Exception ignored) {
        }
        return "4674";
    }

    /**
     * Converte .shp + .dbf em .geojson via streaming registro a registro (memória O(1)),
     * suportando arquivos de múltiplos Gigabytes e milhões de polígonos sem OutOfMemoryError.
     */
    private void converterEmStreaming(Path shpPath, Path dbfPath, String epsg, Path geoJsonDestino) throws IOException {
        JsonFactory jfactory = new JsonFactory();

        try (DataInputStream shpIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(shpPath), IO_BUFFER_SIZE));
             DbfStreamingReader dbfReader = (dbfPath != null && Files.exists(dbfPath)) ? new DbfStreamingReader(dbfPath) : null;
             JsonGenerator jg = jfactory.createGenerator(
                     new BufferedOutputStream(Files.newOutputStream(geoJsonDestino), IO_BUFFER_SIZE),
                     JsonEncoding.UTF8)) {

            byte[] shpHeader = new byte[100];
            try {
                shpIn.readFully(shpHeader);
            } catch (EOFException e) {
                throw new IllegalArgumentException("Arquivo .shp corrompido ou menor que o cabeçalho padrão de 100 bytes.");
            }

            ByteBuffer hdrBuf = ByteBuffer.wrap(shpHeader).order(ByteOrder.BIG_ENDIAN);
            int fileCode = hdrBuf.getInt(0);
            if (fileCode != 9994) {
                throw new IllegalArgumentException("Código de arquivo Shapefile inválido: " + fileCode);
            }

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

            byte[] recBuf = new byte[64 * 1024];

            while (true) {
                int recordNumber;
                int contentLengthWords;
                try {
                    recordNumber = shpIn.readInt();
                    contentLengthWords = shpIn.readInt();
                } catch (EOFException eof) {
                    break; // Fim normal do arquivo .shp
                }

                int recordBytesLen = contentLengthWords * 2;
                if (recordBytesLen <= 0) {
                    continue;
                }
                if (recordBytesLen > recBuf.length) {
                    recBuf = new byte[recordBytesLen];
                }

                try {
                    shpIn.readFully(recBuf, 0, recordBytesLen);
                } catch (EOFException eof) {
                    break;
                }

                Map<String, Object> attrs = dbfReader != null ? dbfReader.lerProximoRegistroAtivo() : null;

                GeometriaShp geom = decodificarRegistroShp(recBuf, recordBytesLen);
                if (geom == null || "Unknown".equals(geom.tipo) || geom.partes.isEmpty()) {
                    continue;
                }

                escreverFeature(jg, geom, attrs);
            }

            jg.writeEndArray();
            jg.writeEndObject();
        }
    }

    private GeometriaShp decodificarRegistroShp(byte[] recBuf, int length) {
        if (length < 4) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(recBuf, 0, length).order(ByteOrder.LITTLE_ENDIAN);
        int shapeType = bb.getInt();

        if (shapeType == 0) { // Null Shape
            return null;
        }

        if (shapeType == 5 || shapeType == 15 || shapeType == 25) { // Polygon, PolygonZ, PolygonM
            if (length < 44) return null;
            bb.position(bb.position() + 32); // pula BBox (minX, minY, maxX, maxY)
            int numParts = bb.getInt();
            int numPoints = bb.getInt();
            if (numParts <= 0 || numPoints <= 0) return null;

            int[] parts = new int[numParts];
            for (int i = 0; i < numParts; i++) {
                parts[i] = bb.getInt();
            }

            List<List<double[]>> rings = new ArrayList<>(numParts);
            for (int i = 0; i < numParts; i++) {
                int start = parts[i];
                int end = (i < numParts - 1) ? parts[i + 1] : numPoints;
                int ringLen = Math.max(0, end - start);
                List<double[]> ring = new ArrayList<>(ringLen + 1);
                for (int pIdx = start; pIdx < end && bb.remaining() >= 16; pIdx++) {
                    double x = bb.getDouble();
                    double y = bb.getDouble();
                    ring.add(new double[]{x, y});
                }
                if (!ring.isEmpty()) {
                    double[] first = ring.get(0);
                    double[] last = ring.get(ring.size() - 1);
                    if (first[0] != last[0] || first[1] != last[1]) {
                        ring.add(new double[]{first[0], first[1]});
                    }
                    rings.add(ring);
                }
            }
            return new GeometriaShp("Polygon", rings);
        } else if (shapeType == 1 || shapeType == 11 || shapeType == 21) { // Point, PointZ, PointM
            if (bb.remaining() < 16) return null;
            double x = bb.getDouble();
            double y = bb.getDouble();
            return new GeometriaShp("Point", List.of(List.of(new double[]{x, y})));
        }

        return new GeometriaShp("Unknown", List.of());
    }

    private void escreverFeature(JsonGenerator jg, GeometriaShp geom, Map<String, Object> attrs) throws IOException {
        jg.writeStartObject();
        jg.writeStringField("type", "Feature");

        jg.writeObjectFieldStart("properties");
        if (attrs != null) {
            for (Map.Entry<String, Object> entry : attrs.entrySet()) {
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
        }
        jg.writeEndObject();

        jg.writeObjectFieldStart("geometry");
        if ("Polygon".equals(geom.tipo)) {
            if (geom.partes.size() == 1) {
                jg.writeStringField("type", "Polygon");
                jg.writeArrayFieldStart("coordinates");
                jg.writeStartArray();
                for (double[] pt : geom.partes.get(0)) {
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
                for (List<double[]> ring : geom.partes) {
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
        } else if ("Point".equals(geom.tipo)) {
            jg.writeStringField("type", "Point");
            jg.writeArrayFieldStart("coordinates");
            double[] pt = geom.partes.get(0).get(0);
            jg.writeNumber(pt[0]);
            jg.writeNumber(pt[1]);
            jg.writeEndArray();
        }
        jg.writeEndObject();

        jg.writeEndObject();
    }

    /**
     * Leitor de arquivos dBASE (.dbf) em streaming, sem alocar o arquivo inteiro na memória.
     */
    private static final class DbfStreamingReader implements AutoCloseable {
        private final DataInputStream in;
        private final int numRecords;
        private final int recordLength;
        private final List<CampoDbf> campos = new ArrayList<>();
        private final byte[] rowBuf;
        private final Charset charset = StandardCharsets.ISO_8859_1;
        private int recordsRead = 0;

        DbfStreamingReader(Path dbfPath) throws IOException {
            this.in = new DataInputStream(new BufferedInputStream(Files.newInputStream(dbfPath), IO_BUFFER_SIZE));
            byte[] headerPrefix = new byte[32];
            try {
                in.readFully(headerPrefix);
            } catch (EOFException e) {
                this.numRecords = 0;
                this.recordLength = 0;
                this.rowBuf = new byte[0];
                return;
            }

            ByteBuffer bb = ByteBuffer.wrap(headerPrefix).order(ByteOrder.LITTLE_ENDIAN);
            this.numRecords = bb.getInt(4);
            int headerLength = Short.toUnsignedInt(bb.getShort(8));
            this.recordLength = Short.toUnsignedInt(bb.getShort(10));
            this.rowBuf = new byte[Math.max(1, this.recordLength)];

            int remainingHeader = Math.max(0, headerLength - 32);
            byte[] descriptorBytes = new byte[remainingHeader];
            in.readFully(descriptorBytes);

            int pos = 0;
            while (pos + 32 <= descriptorBytes.length && descriptorBytes[pos] != 0x0D) {
                int zeroIndex = 0;
                while (zeroIndex < 11 && descriptorBytes[pos + zeroIndex] != 0) {
                    zeroIndex++;
                }
                String nome = new String(descriptorBytes, pos, zeroIndex, StandardCharsets.US_ASCII).trim();
                char tipo = (char) descriptorBytes[pos + 11];
                int length = descriptorBytes[pos + 16] & 0xFF;
                int decimal = descriptorBytes[pos + 17] & 0xFF;

                campos.add(new CampoDbf(nome, tipo, length, decimal));
                pos += 32;
            }
        }

        Map<String, Object> lerProximoRegistroAtivo() throws IOException {
            while (recordsRead < numRecords && recordLength > 0) {
                try {
                    in.readFully(rowBuf, 0, recordLength);
                } catch (EOFException e) {
                    return null;
                }
                recordsRead++;

                byte deleteFlag = rowBuf[0];
                if (deleteFlag == 0x2A) { // '*' indica linha deletada no DBF
                    continue;
                }

                Map<String, Object> props = new LinkedHashMap<>(campos.size());
                int colOffset = 1;
                for (CampoDbf campo : campos) {
                    if (colOffset + campo.length <= recordLength) {
                        String strVal = new String(rowBuf, colOffset, campo.length, charset).trim();
                        if (strVal.isEmpty()) {
                            props.put(campo.nome, null);
                        } else if (campo.tipo == 'N' || campo.tipo == 'F') {
                            try {
                                if (campo.decimal > 0 || strVal.contains(".")) {
                                    props.put(campo.nome, Double.parseDouble(strVal));
                                } else {
                                    props.put(campo.nome, Long.parseLong(strVal));
                                }
                            } catch (NumberFormatException e) {
                                props.put(campo.nome, strVal);
                            }
                        } else if (campo.tipo == 'L') {
                            props.put(campo.nome, "T".equalsIgnoreCase(strVal) || "Y".equalsIgnoreCase(strVal));
                        } else {
                            props.put(campo.nome, strVal);
                        }
                    }
                    colOffset += campo.length;
                }
                return props;
            }
            return null;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private void removerDiretorio(Path dir) {
        if (dir != null && Files.exists(dir)) {
            try (var stream = Files.walk(dir)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            } catch (Exception ignored) {
            }
        }
    }

    private record CampoDbf(String nome, char tipo, int length, int decimal) {}
    private record GeometriaShp(String tipo, List<List<double[]>> partes) {}
}
