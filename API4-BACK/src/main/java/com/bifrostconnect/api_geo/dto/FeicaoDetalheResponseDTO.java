package com.bifrostconnect.api_geo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeicaoDetalheResponseDTO {
    private Long id;
    private Map<String, Object> dadosAlfanumericos;
    private Map<String, Object> calculos;
}