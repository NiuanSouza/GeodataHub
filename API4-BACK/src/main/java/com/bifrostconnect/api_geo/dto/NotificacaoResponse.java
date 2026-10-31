package com.bifrostconnect.api_geo.dto;

import java.time.LocalDateTime;

public record NotificacaoResponse(
        Long id,
        Long usuarioId,
        Long processoId,
        String mensagem,
        Boolean lida,
        LocalDateTime dataCriacao
) {
}
