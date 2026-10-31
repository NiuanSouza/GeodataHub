package com.bifrostconnect.api_geo.service.conversor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
public class ConversorGeoJson implements ConversorArquivoGeoespacial {

    private static final long TAMANHO_MAXIMO = 5L * 1024 * 1024 * 1024; // 5 GB

    @Override
    public boolean suporta(String extensao, Path arquivo) {
        if ("geojson".equalsIgnoreCase(extensao) || "json".equalsIgnoreCase(extensao)) {
            return true;
        }

        if ("zip".equalsIgnoreCase(extensao) && arquivo != null && Files.isRegularFile(arquivo)) {
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(arquivo))) {
                ZipEntry entrada;
                boolean temGeoJson = false;
                boolean temShp = false;

                while ((entrada = zip.getNextEntry()) != null) {
                    String nome = entrada.getName().toLowerCase(Locale.ROOT);
                    if (!entrada.isDirectory()) {
                        if (nome.endsWith(".geojson") || nome.endsWith(".json")) {
                            temGeoJson = true;
                        }
                        if (nome.endsWith(".shp")) {
                            temShp = true;
                        }
                    }
                    zip.closeEntry();
                }
                return temGeoJson && !temShp;
            } catch (Exception ignored) {
                return false;
            }
        }

        return false;
    }

    @Override
    public Path converterParaGeoJson(Path arquivoOrigem) throws IOException {
        String nome = arquivoOrigem.getFileName().toString().toLowerCase(Locale.ROOT);

        if (nome.endsWith(".geojson") || nome.endsWith(".json")) {
            return arquivoOrigem;
        }

        if (!nome.endsWith(".zip")) {
            throw new IllegalArgumentException("Formato incompatível com o conversor GeoJSON: " + nome);
        }

        Path extraido = null;

        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(arquivoOrigem))) {
            ZipEntry entrada;
            int quantidadeGeoJson = 0;

            while ((entrada = zip.getNextEntry()) != null) {
                String nomeEntrada = entrada.getName().toLowerCase(Locale.ROOT);

                if (entrada.isDirectory() || (!nomeEntrada.endsWith(".geojson") && !nomeEntrada.endsWith(".json"))) {
                    zip.closeEntry();
                    continue;
                }

                quantidadeGeoJson++;

                if (quantidadeGeoJson > 1) {
                    throw new IllegalArgumentException(
                            "O ZIP contém mais de um arquivo GeoJSON. Envie um ZIP com apenas um GeoJSON.");
                }

                extraido = Files.createTempFile("geojson-extraido-", ".geojson");

                try (OutputStream saida = Files.newOutputStream(extraido)) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int bytesLidos;

                    while ((bytesLidos = zip.read(buffer)) != -1) {
                        total += bytesLidos;

                        if (total > TAMANHO_MAXIMO) {
                            throw new IllegalArgumentException(
                                    "O GeoJSON extraído excede o limite de 5 GB.");
                        }

                        saida.write(buffer, 0, bytesLidos);
                    }
                }

                zip.closeEntry();
            }

            if (quantidadeGeoJson == 0) {
                throw new IllegalArgumentException("O ZIP não contém nenhum arquivo .geojson ou .json.");
            }

            Path resultado = extraido;
            extraido = null;
            return resultado;

        } finally {
            if (extraido != null) {
                Files.deleteIfExists(extraido);
            }
        }
    }
}
