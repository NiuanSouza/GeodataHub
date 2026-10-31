package com.bifrostconnect.api_geo.exception;

import java.sql.SQLException;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// Tarefa 15: o PostGIS estourou no meio de uma conta geométrica ou de uma reprojeção
// (divisão por zero, PROJ sem conseguir projetar, TopologyException do GEOS...).
// É problema da geometria da carga, não do servidor, por isso já nasce como 422:
// qualquer controller que pega ResponseStatusException devolve o status certo sem mexer em nada
public class FalhaGeoespacialException extends ResponseStatusException {

    // o código vai pra coluna codigo_erro do log_processamento (VARCHAR(30))
    public enum Tipo {
        DIVISAO_POR_ZERO("GEO_DIVISAO_POR_ZERO", "divisão por zero"),
        PROJECAO("GEO_ERRO_PROJECAO", "erro de projeção/reprojeção de coordenadas"),
        TOPOLOGIA("GEO_ERRO_TOPOLOGIA", "erro topológico do GEOS"),
        GEOMETRIA("GEO_ERRO_GEOMETRIA", "erro geométrico no PostGIS");

        private final String codigo;
        private final String descricao;

        Tipo(String codigo, String descricao) {
            this.codigo = codigo;
            this.descricao = descricao;
        }

        public String getCodigo() {
            return codigo;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    // a mensagem do PostgreSQL pode vir com o SQL inteiro junto, então corta
    private static final int TAMANHO_MAX_DETALHE = 500;

    private final String operacao;
    private final Tipo tipo;
    private final String detalheTecnico;

    public FalhaGeoespacialException(String operacao, Throwable causa) {
        this(operacao, classificar(causa), mensagemRaiz(causa), causa);
    }

    private FalhaGeoespacialException(String operacao, Tipo tipo, String detalheTecnico, Throwable causa) {
        super(HttpStatus.UNPROCESSABLE_ENTITY,
                "Falha geoespacial na " + operacao + " (" + tipo.getDescricao() + "): " + detalheTecnico,
                causa);
        this.operacao = operacao;
        this.tipo = tipo;
        this.detalheTecnico = detalheTecnico;
    }

    public String getOperacao() {
        return operacao;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public String getCodigoErro() {
        return tipo.getCodigo();
    }

    public String getDetalheTecnico() {
        return detalheTecnico;
    }

    // só conta como erro geoespacial o que veio do banco (PostGIS) ou de conta com zero;
    // arquivo sumido, NPE etc. continuam seguindo o caminho de falha técnica de antes
    public static boolean ehErroGeoespacial(Throwable erro) {
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof FalhaGeoespacialException
                    || t instanceof SQLException
                    || t instanceof DataAccessException
                    || t instanceof ArithmeticException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    // olha o SQLState e o texto de toda a cadeia de causas, porque o Spring embrulha
    // o PSQLException em DataAccessException e a mensagem útil fica lá no fundo
    public static Tipo classificar(Throwable erro) {
        StringBuilder textos = new StringBuilder();
        for (Throwable t = erro; t != null; t = t.getCause()) {
            if (t instanceof ArithmeticException) {
                return Tipo.DIVISAO_POR_ZERO;
            }
            if (t instanceof SQLException sql && "22012".equals(sql.getSQLState())) {
                return Tipo.DIVISAO_POR_ZERO;
            }
            if (t.getMessage() != null) {
                textos.append(' ').append(t.getMessage().toLowerCase(Locale.ROOT));
            }
            if (t.getCause() == t) {
                break;
            }
        }

        String texto = textos.toString();
        if (texto.contains("division by zero") || texto.contains("divisão por zero")) {
            return Tipo.DIVISAO_POR_ZERO;
        }
        if (texto.contains("transform") || texto.contains("project") || texto.contains("srid")
                || texto.contains("spatial_ref_sys") || texto.contains("proj_")) {
            return Tipo.PROJECAO;
        }
        if (texto.contains("topologyexception") || texto.contains("geos")
                || texto.contains("side location conflict") || texto.contains("non-noded")) {
            return Tipo.TOPOLOGIA;
        }
        return Tipo.GEOMETRIA;
    }

    // mensagem da causa mais funda (a do PostgreSQL), sem o "ERROR:" e sem quebra de linha
    static String mensagemRaiz(Throwable erro) {
        Throwable raiz = erro;
        while (raiz != null && raiz.getCause() != null && raiz.getCause() != raiz) {
            raiz = raiz.getCause();
        }
        String mensagem = raiz == null || raiz.getMessage() == null
                ? (raiz == null ? "erro desconhecido" : raiz.getClass().getSimpleName())
                : raiz.getMessage();

        mensagem = mensagem.replaceFirst("^ERROR:\\s*", "").replaceAll("\\s+", " ").trim();
        return mensagem.length() > TAMANHO_MAX_DETALHE
                ? mensagem.substring(0, TAMANHO_MAX_DETALHE) + "..."
                : mensagem;
    }
}
