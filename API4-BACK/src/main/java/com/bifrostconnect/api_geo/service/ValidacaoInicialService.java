package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.repository.CamadaReferenciaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ValidacaoInicialService {

    private final ObjectMapper objectMapper;
    private final CamadaReferenciaRepository camadaReferenciaRepository;

    public ValidacaoInicialService(ObjectMapper objectMapper, CamadaReferenciaRepository camadaReferenciaRepository) {
        this.objectMapper = objectMapper;
        this.camadaReferenciaRepository = camadaReferenciaRepository;
    }

    /**
     * Motor da Etapa 1. Retorna uma mensagem de erro se o arquivo estiver corrompido
     * ou tiver duplicidade, caso contrário retorna null (Válido).
     */
    public String executarValidacaoEtapa1(Path caminhoFisico, String nomeOriginal) {
        try {
            // 1. Checagem de corrupção e abertura de pacote
            validarIntegridadeFisica(caminhoFisico, nomeOriginal);

            // 2. Checagem de IDs duplicados
            validarDuplicidadeDeIds(caminhoFisico, nomeOriginal);

            return null; // Passou em tudo
            
        } catch (Exception e) {
            return e.getMessage(); // Motivo exato da falha que será salvo no LogValidacao
        }
    }

    private void validarIntegridadeFisica(Path caminhoFisico, String nomeOriginal) throws Exception {
        String nome = nomeOriginal.toLowerCase();

        if (nome.endsWith(".zip")) {
            validarPacoteZip(caminhoFisico);
        } else if (nome.endsWith(".geojson") || nome.endsWith(".json")) {
            validarArquivoGeoJson(caminhoFisico);
        } else {
            throw new Exception("Formato não suportado. Envie um arquivo .zip ou .geojson.");
        }
    }

    private void validarPacoteZip(Path caminhoFisico) throws Exception {
        try (InputStream is = Files.newInputStream(caminhoFisico);
             ZipInputStream zis = new ZipInputStream(is)) {
             
            boolean pacoteTemConteudo = false;
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                pacoteTemConteudo = true;
                zis.closeEntry();
            }
            if (!pacoteTemConteudo) {
                throw new Exception("O pacote ZIP está vazio ou não possui arquivos legíveis.");
            }
        } catch (Exception e) {
            throw new Exception("O pacote ZIP físico está corrompido ou ilegível.");
        }
    }

    private void validarArquivoGeoJson(Path caminhoFisico) throws Exception {
        try (InputStream is = Files.newInputStream(caminhoFisico)) {
            JsonNode rootNode = objectMapper.readTree(is);
            
            if (rootNode == null || !rootNode.has("type") || !rootNode.get("type").asText().equals("FeatureCollection")) {
                throw new Exception("O arquivo GeoJSON não possui a estrutura raiz 'FeatureCollection'.");
            }
        } catch (Exception e) {
            throw new Exception("O arquivo GeoJSON está corrompido, malformado ou ilegível.");
        }
    }

    private void validarDuplicidadeDeIds(Path caminhoFisico, String nomeOriginal) throws Exception {
        String nome = nomeOriginal.toLowerCase();
        List<String> idsExtraidos = new ArrayList<>();

        if (nome.endsWith(".geojson") || nome.endsWith(".json")) {
            try (InputStream is = Files.newInputStream(caminhoFisico)) {
                JsonNode rootNode = objectMapper.readTree(is);
                JsonNode features = rootNode.get("features");
                
                if (features != null && features.isArray() && !features.isEmpty()) {
                    for (JsonNode feature : features) {
                        JsonNode properties = feature.get("properties");
                        
                        // Tenta localizar a chave de ID (ajuste as chaves conforme o padrão dos seus shapefiles/geojsons)
                        if (properties != null) {
                            if (properties.has("id")) {
                                idsExtraidos.add(properties.get("id").asText());
                            } else if (properties.has("OBJECTID")) {
                                idsExtraidos.add(properties.get("OBJECTID").asText());
                            } else if (properties.has("identificador")) {
                                idsExtraidos.add(properties.get("identificador").asText());
                            }
                        }
                    }
                }
            }
        }

        if (!idsExtraidos.isEmpty()) {
            boolean existeDuplicado = camadaReferenciaRepository.existemIdentificadoresDuplicados(idsExtraidos);
            if (existeDuplicado) {
                throw new Exception("Foram encontrados cadastros/IDs duplicados na base de dados oficial.");
            }
        }
    }
}