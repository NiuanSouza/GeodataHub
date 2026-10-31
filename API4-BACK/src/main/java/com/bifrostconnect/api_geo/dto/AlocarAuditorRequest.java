package com.bifrostconnect.api_geo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlocarAuditorRequest {

    @NotNull(message = "O ID do auditor é obrigatório.")
    private Long auditorId;

    private Long gestorId;
}
