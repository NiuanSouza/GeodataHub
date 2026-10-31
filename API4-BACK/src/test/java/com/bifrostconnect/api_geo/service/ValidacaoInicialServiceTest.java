package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.repository.CamadaReferenciaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidacaoInicialServiceTest {

    @Mock
    private CamadaReferenciaRepository camadaReferenciaRepository;

    private ObjectMapper objectMapper;

    private ValidacaoInicialService validacaoInicialService;

    // O JUnit 5 cria e destrói um diretório temporário automaticamente para cada teste
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Usamos um ObjectMapper real porque a leitura de JSON faz parte do motor
        objectMapper = new ObjectMapper();
        validacaoInicialService = new ValidacaoInicialService(objectMapper, camadaReferenciaRepository);
    }

    @Test
    void deveRetornarErroQuandoZipCorrompido() throws Exception {
        // TAREFA 22: Testar o motor enviando um arquivo .zip corrompido
        Path zipPath = tempDir.resolve("arquivo_corrompido.zip");
        Files.writeString(zipPath, "conteudo em bytes invalido que quebra a leitura do zip");

        String resultado = validacaoInicialService.executarValidacaoEtapa1(zipPath, "arquivo_corrompido.zip");

        assertNotNull(resultado, "Deveria retornar uma mensagem de erro");
        assertTrue(resultado.contains("corrompido"), "A mensagem de erro deve indicar que o ZIP está corrompido");
    }

    @Test
    void deveRetornarErroQuandoGeoJsonTiverIdsDuplicados() throws Exception {
        // TAREFA 22: Testar uma base com registros de mesmo código identificador
        Path geojsonPath = tempDir.resolve("malha_duplicada.geojson");
        String geojsonContent = """
            {
              "type": "FeatureCollection",
              "features": [
                { "type": "Feature", "properties": { "id": "123" }, "geometry": null },
                { "type": "Feature", "properties": { "id": "123" }, "geometry": null }
              ]
            }
            """;
        Files.writeString(geojsonPath, geojsonContent);

        // Simulamos o comportamento do banco retornando "true" para a query de duplicidade
        when(camadaReferenciaRepository.existemIdentificadoresDuplicados(anyList())).thenReturn(true);

        String resultado = validacaoInicialService.executarValidacaoEtapa1(geojsonPath, "malha_duplicada.geojson");

        assertNotNull(resultado, "Deveria retornar uma mensagem de erro de duplicidade");
        assertTrue(resultado.contains("duplicados"), "A mensagem de erro deve indicar a existência de IDs duplicados");
    }

    @Test
    void deveRetornarNullQuandoGeoJsonForTotalmenteValido() throws Exception {
        // Teste de sucesso (Caminho feliz)
        Path geojsonPath = tempDir.resolve("malha_valida.geojson");
        String geojsonContent = """
            {
              "type": "FeatureCollection",
              "features": [
                { "type": "Feature", "properties": { "id": "999" }, "geometry": null }
              ]
            }
            """;
        Files.writeString(geojsonPath, geojsonContent);

        when(camadaReferenciaRepository.existemIdentificadoresDuplicados(anyList())).thenReturn(false);

        String resultado = validacaoInicialService.executarValidacaoEtapa1(geojsonPath, "malha_valida.geojson");

        assertNull(resultado, "Deveria retornar null (sem erros) para um arquivo íntegro e sem duplicidades.");
    }
}