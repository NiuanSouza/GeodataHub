package com.bifrostconnect.api_geo.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.service.ArquivoProcessadoService;

@RestController
@RequestMapping("/arquivos-processados")
public class ArquivoProcessadoController {

    private final ArquivoProcessadoService arquivoProcessadoService;

    public ArquivoProcessadoController(
            ArquivoProcessadoService arquivoProcessadoService) {
        this.arquivoProcessadoService = arquivoProcessadoService;
    }

    @PostMapping("/copiar")
    public ResponseEntity<?> criarCopiaProcessada(
            @RequestParam Long arquivoOriginalId,
            @RequestParam Long processoId,
            @RequestParam Long etapaGeracaoId,
            @RequestParam(required = false) String epsg) {

        try {
            ArquivoProcessado arquivoProcessado =
                    arquivoProcessadoService.criarCopiaProcessada(
                            arquivoOriginalId,
                            processoId,
                            etapaGeracaoId,
                            epsg
                    );

            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(arquivoProcessado);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(Map.of("erro", e.getMessage()));

        } catch (Exception e) {

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("erro", e.getMessage()));
        }
    }
}