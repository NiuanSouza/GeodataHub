package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.dto.FeicaoDetalheResponseDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class GeoJsonConversaoTest {

    @Test
    public void deveValidarSerializacaoGeoJsonComDtoReal() throws Exception {
        Map<String, Object> dadosMock = new HashMap<>();
        dadosMock.put("hectares", 125.5);
        dadosMock.put("origem", "shapefile_sidrolandia");

        FeicaoDetalheResponseDTO dto = FeicaoDetalheResponseDTO.builder()
                .id(1L)
                .dadosAlfanumericos(dadosMock)
                .calculos(dadosMock)
                .build();

        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(dto);

        JsonNode rootNode = objectMapper.readTree(jsonString);

        assertNotNull(rootNode.get("id"), "O ID da feição deve estar presente");
        assertNotNull(rootNode.get("calculos"), "Os cálculos analíticos (hectares) devem estar presentes");
        
        // Correção aplicada aqui (parêntese fechando corretamente)
        assertEquals(125.5, rootNode.get("calculos").get("hectares").asDouble(), "O valor em hectares deve ser preservado na serialização");
    }
}