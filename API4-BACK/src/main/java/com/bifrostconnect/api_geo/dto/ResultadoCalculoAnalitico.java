package com.bifrostconnect.api_geo.dto;

import java.math.BigDecimal;
import java.util.List;

public record ResultadoCalculoAnalitico(
        int poligonosCalculados,
        BigDecimal areaTotalHectares,
        List<Cruzamento> cruzamentos,
        List<String> inconsistencias) {

    // as fontes de cada categoria vêm do application.properties (calculo-analitico.fontes.*)
    public enum Categoria {
        APP,
        RESERVA_LEGAL,
        ALERTA
    }

    // uma linha gravada em resultados_calculos: imóvel x polígono de uma camada de referência
    public record Cruzamento(
            String codigoImovel,
            Categoria categoria,
            String fonteCamada,
            String codigoAlerta,
            BigDecimal hectaresSobrepostos) {}

    // soma simples das linhas; se duas camadas da mesma categoria se encavalam, conta duas vezes
    public BigDecimal hectaresPorCategoria(Categoria categoria) {
        return cruzamentos.stream()
                .filter(c -> c.categoria() == categoria)
                .map(Cruzamento::hectaresSobrepostos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // conta que não fecha (área zerada, cruzamento maior que o imóvel...) -> quarentena (Tarefa 12)
    public boolean possuiInconsistencias() {
        return !inconsistencias.isEmpty();
    }

    public long contar(Categoria categoria) {
        return cruzamentos.stream().filter(c -> c.categoria() == categoria).count();
    }
}
