package com.bifrostconnect.api_geo.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AprovacaoTratamentoRequest {
    @NotNull(message = "O id do gestor é obrigatório.")
    private Long gestorId;

    // na aprovação o parecer é opcional (a V22 só exige justificativa na reprovação)
    private String parecer;
}
