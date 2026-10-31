package com.bifrostconnect.api_geo.dto;

import java.util.List;

import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;

// Tarefa 14: filtros do cruzamento que o Analista monta na tela (POST /api/analise/cruzamento).
// Tudo é opcional: sem nada, cruza todas as bases publicadas com todas as camadas configuradas.
// processoIds = bases publicadas específicas; conjunto/safra/órgão = mesmos filtros do mapa (V23).
// fontes tem prioridade sobre categorias; sem nenhuma das duas vale tudo que está no application.properties
public record CruzamentoAnaliseRequest(
        List<Long> processoIds,
        Long conjuntoId,
        String anoSafra,
        Long orgaoId,
        List<Categoria> categorias,
        List<String> fontes) {

    public List<Long> processoIds() {
        return processoIds == null ? List.of() : processoIds;
    }

    public List<Categoria> categorias() {
        return categorias == null ? List.of() : categorias;
    }

    public List<String> fontes() {
        return fontes == null ? List.of()
                : fontes.stream().filter(f -> f != null && !f.isBlank()).map(String::trim).distinct().toList();
    }
}
