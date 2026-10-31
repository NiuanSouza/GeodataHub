package com.bifrostconnect.api_geo.dto;

import java.util.Map;

public record ProcessoMetricasResponse(
        Long totalProcessos,
        Map<String, Long> porSituacao,
        Map<String, Long> porEtapa,
        Map<String, Long> porConjunto
) {
}