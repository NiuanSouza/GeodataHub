package com.bifrostconnect.api_geo.controller;

import com.bifrostconnect.api_geo.dto.FeicaoDetalheResponseDTO;
import com.bifrostconnect.api_geo.entity.CamadaReferencia;
import com.bifrostconnect.api_geo.repository.CamadaReferenciaRepository;
import com.bifrostconnect.api_geo.service.CamadasMapaService;
import com.bifrostconnect.api_geo.service.MapaService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mapa")
public class MapaController {

    private final MapaService mapaService;
    private final CamadaReferenciaRepository camadaReferenciaRepository;
    private final CamadasMapaService camadasMapaService;

    public MapaController(
            MapaService mapaService,
            CamadaReferenciaRepository camadaReferenciaRepository,
            CamadasMapaService camadasMapaService) {

        this.mapaService = mapaService;
        this.camadaReferenciaRepository = camadaReferenciaRepository;
        this.camadasMapaService = camadasMapaService;
    }

    /**
     * Tarefa 16: detalhes de uma feição selecionada no mapa.
     */
    @GetMapping("/feicoes/{id_poligono}")
    public ResponseEntity<FeicaoDetalheResponseDTO> buscarDetalhesFeicao(
            @PathVariable("id_poligono") Long idPoligono) {

        FeicaoDetalheResponseDTO resposta =
                mapaService.buscarDetalhesPorId(idPoligono);

        return ResponseEntity.ok(resposta);
    }

    /**
     * Tarefas 15 e 17: rota de pontos monitorados / bases cadastradas para o painel do mapa.
     */
    @GetMapping("/camadas-legado")
    public ResponseEntity<List<Map<String, Object>>> listarCamadasAnalista(
            @RequestParam(required = false) String conjunto,
            @RequestParam(required = false) Integer ano) {

        List<Map<String, Object>> bases = camadasMapaService.listarLocaisBasesPublicadas();
        return ResponseEntity.ok(bases);
    }

    /**
     * Tarefa 6 / Tarefa 14: catálogo de camadas com paginação e filtros de busca.
     *
     * page: número da página, começando em zero.
     * size: quantidade de feições por página.
     * conjuntoId, anoSafra, bbox e busca: filtros opcionais.
     */
    @PreAuthorize("hasAnyRole('ANALISTA', 'AUDITOR', 'ADMINISTRADOR', 'OPERADOR')")
    @GetMapping({"/camadas", "/camadas-geojson"})
    public ResponseEntity<Map<String, Object>> listarCamadasMapaGeoJson(
            @RequestParam(required = false) Long conjuntoId,
            @RequestParam(required = false) String anoSafra,
            @RequestParam(required = false) String bbox,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long processoId) {

        if (page < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O parâmetro 'page' não pode ser negativo.");
        }

        if (size < 1 || size > 500) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O parâmetro 'size' deve estar entre 1 e 500.");
        }

        Map<String, Object> geoJson =
                camadasMapaService.buscarCamadasPorFiltros(
                        conjuntoId,
                        anoSafra,
                        bbox,
                        page,
                        size,
                        busca,
                        processoId);

        return ResponseEntity.ok(geoJson);
    }
}
