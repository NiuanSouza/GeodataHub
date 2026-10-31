package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class HashServiceTest {

    private final HashService hashService = new HashService();

    @Test
    @DisplayName("Deve garantir que o mesmo arquivo resulte sempre no mesmo hash")
    void deveGerarOMesmoHashParaOMesmoArquivo() throws Exception {
        byte[] conteudo = "conteudo_geo_ficticio_123".getBytes();
        
        // Simula o upload do mesmo arquivo duas vezes para garantir consistência
        MockMultipartFile arquivo1 = new MockMultipartFile("file", "mapa.geojson", "application/json", conteudo);
        MockMultipartFile arquivo2 = new MockMultipartFile("file", "mapa.geojson", "application/json", conteudo);

        String hash1 = hashService.calcularHash(arquivo1);
        String hash2 = hashService.calcularHash(arquivo2);

        assertNotNull(hash1);
        assertEquals(hash1, hash2, "Os hashes devem ser idênticos para o mesmo conteúdo de arquivo.");
    }
}