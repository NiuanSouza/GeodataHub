package com.bifrostconnect.api_geo.controller;

import com.bifrostconnect.api_geo.service.CatalogoBasesService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// US09 - Catálogo de bases publicadas que o Analista consulta
@RestController
@RequestMapping("/catalogo")
@PreAuthorize("hasAnyRole('ANALISTA', 'AUDITOR', 'ADMINISTRADOR', 'OPERADOR')")
public class CatalogoController {

    private final CatalogoBasesService catalogoBasesService;

    public CatalogoController(CatalogoBasesService catalogoBasesService) {
        this.catalogoBasesService = catalogoBasesService;
    }

    // Tarefa 7 + 8: só bases em Publicação/Concluída; catálogo vazio (ou filtro sem resultado) é 204
    @GetMapping("/bases")
    public ResponseEntity<List<Map<String, Object>>> listarBases(
            @RequestParam(required = false) Long conjuntoId,
            @RequestParam(required = false) String anoSafra) {

        List<Map<String, Object>> bases = catalogoBasesService.listarBasesPublicadas(conjuntoId, anoSafra);

        if (bases.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(bases);
    }

    // Tarefa 8: ID que não existe (ou que não está publicado) cai no 404 do GlobalExceptionHandler
    @GetMapping("/bases/{id_base}")
    public ResponseEntity<Map<String, Object>> buscarDetalhesBase(@PathVariable("id_base") Long idBase) {
        return ResponseEntity.ok(catalogoBasesService.buscarDetalhesBase(idBase));
    }
}
