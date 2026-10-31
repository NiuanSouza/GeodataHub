package com.bifrostconnect.api_geo.controller;

import com.bifrostconnect.api_geo.dto.AprovacaoTratamentoRequest;
import com.bifrostconnect.api_geo.service.ConsolidacaoPublicacaoService;
import com.bifrostconnect.api_geo.service.RevisaoPublicacaoService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/publicacao")
public class PublicacaoController {

    private final RevisaoPublicacaoService revisaoPublicacaoService;
    private final ConsolidacaoPublicacaoService consolidacaoPublicacaoService;

    public PublicacaoController(
            RevisaoPublicacaoService revisaoPublicacaoService,
            ConsolidacaoPublicacaoService consolidacaoPublicacaoService) {
        this.revisaoPublicacaoService = revisaoPublicacaoService;
        this.consolidacaoPublicacaoService = consolidacaoPublicacaoService;
    }

    /**
     * Tarefa 12: aprovação do tratamento e consolidação para publicação.
     */
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMINISTRADOR')")
    @PostMapping("/{id_carga}/aprovar-tratamento")
    public ResponseEntity<Map<String, Object>> aprovarTratamento(
            @PathVariable("id_carga") Long idCarga,
            @Valid @RequestBody AprovacaoTratamentoRequest request) {

        Map<String, Object> resultado =
                consolidacaoPublicacaoService.aprovarTratamento(
                        idCarga,
                        request.getGestorId(),
                        request.getParecer());

        return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
    }

    /**
     * Tarefa 13: dados para revisão de publicação.
     */
    @PreAuthorize("hasAnyRole('AUDITOR', 'ADMINISTRADOR', 'OPERADOR')")
    @GetMapping("/{id_carga}/revisao")
    public ResponseEntity<Map<String, Object>> obterRevisaoPublicacao(
            @PathVariable("id_carga") Long idCarga) {

        Map<String, Object> resultado =
                revisaoPublicacaoService.obterDadosRevisao(idCarga);

        return ResponseEntity.ok(resultado);
    }
}

