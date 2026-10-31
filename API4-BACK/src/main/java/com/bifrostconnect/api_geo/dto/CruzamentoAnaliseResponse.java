package com.bifrostconnect.api_geo.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.bifrostconnect.api_geo.dto.CalculosCargaResponse.TotalCategoria;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;

// Tarefa 14: resultado do cruzamento das bases publicadas pro Analista.
// Só entram as propriedades que caíram em pelo menos uma camada cruzada
public record CruzamentoAnaliseResponse(
        int basesCruzadas,
        List<String> fontesCruzadas,
        int propriedadesAfetadas,
        BigDecimal hectaresAfetados,
        Map<Categoria, TotalCategoria> totaisPorCategoria,
        List<PropriedadeAfetada> propriedades) {

    // hectaresAfetados é a soma das sobreposições; se duas camadas se encavalam no mesmo
    // pedaço do imóvel, conta duas vezes (mesma regra do hectares_sobrepostos_total da V23)
    public record PropriedadeAfetada(
            Long processoId,
            String versaoOficial,
            String conjunto,
            String orgao,
            String anoSafra,
            String codigoImovel,
            BigDecimal areaImovelHectares,
            BigDecimal hectaresAfetados,
            List<Sobreposicao> sobreposicoes) {}

    // categoria pode vir null se a fonte saiu do application.properties depois do cálculo
    public record Sobreposicao(
            Categoria categoria,
            String fonteCamada,
            String codigoAlerta,
            BigDecimal hectaresSobrepostos) {}
}
