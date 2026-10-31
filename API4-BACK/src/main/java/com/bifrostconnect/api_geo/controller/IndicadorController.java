package com.bifrostconnect.api_geo.controller;

import com.bifrostconnect.api_geo.dto.LinhagemResponseDTO;
import com.bifrostconnect.api_geo.service.LinhagemService;
import com.bifrostconnect.api_geo.service.ProcessoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/indicadores")
@RequiredArgsConstructor
public class IndicadorController {

    private final LinhagemService linhagemService;
    private final ProcessoService processoService;

    @GetMapping("/{id}/linhagem")
    public ResponseEntity<LinhagemResponseDTO> consultarLinhagem(@PathVariable Long id) {
        LinhagemResponseDTO linhagem = linhagemService.buscarLinhagem(id);
        return ResponseEntity.ok(linhagem);
    }

    @PatchMapping("/{id}/fechar")
    public ResponseEntity<Void> fecharIndicador(
            @PathVariable Long id,
            @RequestHeader(value = "X-Gestor-ID", required = false) Long gestorIdHeader,
            @RequestParam(value = "gestorId", required = false) Long gestorIdParam) {
        
        Long gestorId = gestorIdHeader != null ? gestorIdHeader : (gestorIdParam != null ? gestorIdParam : 1L);
        processoService.fecharIndicador(id, gestorId);
        return ResponseEntity.noContent().build();
    }

    // Tarefa 7 (API de Exportação): Rota GET /api/indicadores/{id}/exportar (ou /versoes/{id}/exportar)
    @GetMapping("/{id}/exportar")
    public ResponseEntity<Resource> exportarVersao(
            @PathVariable Long id,
            @RequestParam(value = "formato", defaultValue = "geojson") String formato) {
        
        Resource arquivo = processoService.exportarArquivoConsolidado(id, formato);
        
        String contentType = formato.equalsIgnoreCase("csv") ? "text/csv" : "application/geo+json";
        String extensao = formato.equalsIgnoreCase("csv") ? "csv" : "geojson";

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"exportado_" + id + "." + extensao + "\"")
                .body(arquivo);
    }
}