package com.bifrostconnect.api_geo.dto;

import java.math.BigDecimal;
import java.util.List;


public record ResultadoValidacaoTopologica(
        int totalFeicoes,
        int feicoesCarregadas,
        List<Problema> problemas) {

    // cada tipo já sabe qual regra_validacao usar na hora de gravar a ocorrência.
    // inaceitavel = manda a carga pra quarentena (Tarefa 12). Bate com as regras CRITICA;
    // geometria inválida (ALTA) só gera aviso, o cálculo já pula esses polígonos
    public enum TipoProblema {
        GEOMETRIA_INVALIDA("GEOMETRIA_INVALIDA", false),
        CODIGO_IMOVEL_DUPLICADO("CAR_DUPLICADO", true),
        POLIGONO_DUPLICADO("POLIGONO_DUPLICADO", true),
        POLIGONO_SOBREPOSTO("SOBREPOSICAO_PROPRIEDADE", true);

        private final String regra;
        private final boolean inaceitavel;

        TipoProblema(String regra, boolean inaceitavel) {
            this.regra = regra;
            this.inaceitavel = inaceitavel;
        }

        public String getRegra() {
            return regra;
        }

        public boolean isInaceitavel() {
            return inaceitavel;
        }
    }

    // codigoImovelRelacionado e hectares só vêm preenchidos quando o problema envolve
    // dois polígonos (duplicado ou sobreposto)
    public record Problema(
            TipoProblema tipo,
            String codigoImovel,
            String codigoImovelRelacionado,
            BigDecimal hectaresSobrepostos,
            String mensagem) {

        public static Problema daFeicao(TipoProblema tipo, String codigoImovel, String mensagem) {
            return new Problema(tipo, codigoImovel, null, null, mensagem);
        }
    }

    public boolean possuiProblemas() {
        return !problemas.isEmpty();
    }

    public List<Problema> problemasInaceitaveis() {
        return problemas.stream().filter(p -> p.tipo().isInaceitavel()).toList();
    }

    public long contar(TipoProblema tipo) {
        return problemas.stream().filter(p -> p.tipo() == tipo).count();
    }
}
