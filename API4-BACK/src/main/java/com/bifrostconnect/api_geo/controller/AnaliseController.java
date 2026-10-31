package com.bifrostconnect.api_geo.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseRequest;
import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseResponse;
import com.bifrostconnect.api_geo.service.AnaliseCruzamentoService;

@RestController
@RequestMapping("/analise")
public class AnaliseController {

    private final AnaliseCruzamentoService analiseCruzamentoService;

    public AnaliseController(AnaliseCruzamentoService analiseCruzamentoService) {
        this.analiseCruzamentoService = analiseCruzamentoService;
    }

    // Tarefa 14: cruza as bases publicadas e devolve as propriedades e hectares afetados.
    // Body vazio ({}) cruza tudo que está publicado com todas as camadas configuradas
    @PostMapping("/cruzamento")
    public ResponseEntity<?> cruzar(@RequestBody(required = false) CruzamentoAnaliseRequest request) {
        try {
            CruzamentoAnaliseResponse resultado = analiseCruzamentoService.cruzar(
                    request == null ? new CruzamentoAnaliseRequest(null, null, null, null, null, null) : request);
            return ResponseEntity.ok(resultado);

        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(
                    Map.of("erro", e.getReason() == null ? "Falha ao cruzar as bases publicadas." : e.getReason()));
        }
    }
}
