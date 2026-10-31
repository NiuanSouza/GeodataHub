package com.bifrostconnect.api_geo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CancelamentoRequest {
    @NotBlank(message = "O motivo do cancelamento é obrigatório.")
    private String motivo;
}