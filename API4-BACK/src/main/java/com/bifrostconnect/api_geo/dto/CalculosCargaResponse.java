package com.bifrostconnect.api_geo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;

// Tarefa 13: o que o Tratamento calculou pra uma carga, do jeito que as telas
// do Gestor, Auditor e Analista consomem (GET /api/cargas/{id}/calculos)
public record CalculosCargaResponse(
        Long processoId,
        Long arquivoProcessadoId,
        String etapa,
        String situacao,
        int poligonosCalculados,
        BigDecimal areaTotalHectares,
        List<AreaPoligono> poligonos,
        Map<Categoria, TotalCategoria> totaisPorCategoria,
        List<Intersecao> intersecoes,
        List<Intersecao> alertasAmbientais) {

    public record AreaPoligono(String codigoImovel, BigDecimal areaHectares) {}

    // categoria pode vir null se a fonte saiu do application.properties depois do cálculo
    public record Intersecao(
            String codigoImovel,
            Categoria categoria,
            String fonteCamada,
            String codigoAlerta,
            BigDecimal hectaresSobrepostos) {}

    public record TotalCategoria(long quantidade, BigDecimal hectares) {}
}
