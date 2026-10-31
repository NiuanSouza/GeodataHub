package com.bifrostconnect.api_geo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecisaoGestorRequest {

    @NotNull(message = "O ID do gestor é obrigatório.")
    private Long gestorId;

    @NotBlank(message = "A etapa avaliada é obrigatória (VALIDACAO, TRATAMENTO, PUBLICACAO).")
    private String etapa;

    @NotBlank(message = "A decisão é obrigatória (APROVADO, REPROVADO).")
    private String decisao;

    // Parecer/justificativa é obrigatório no caso de REPROVADO (conforme Trigger V22)
    private String parecer;
}
