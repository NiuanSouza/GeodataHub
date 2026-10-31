package com.bifrostconnect.api_geo.dto;

import java.time.LocalDateTime;

public record LogEspacialResponse(
        Long id,
        Long processoId,
        Long processoEtapaId,
        Long etapaId,
        String etapa,
        Long situacaoId,
        String situacao,
        Integer tentativa,
        String nivel,
        String mensagem,
        String codigoErro,
        String detalhes,
        LocalDateTime dataHora
) {
}