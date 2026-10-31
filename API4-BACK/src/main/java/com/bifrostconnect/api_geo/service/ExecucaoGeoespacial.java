package com.bifrostconnect.api_geo.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.bifrostconnect.api_geo.exception.FalhaGeoespacialException;

// Tarefa 15: try/catch em volta de toda chamada ao PostGIS que faz conta geométrica ou
// converte coordenada. Faz duas coisas:
//  1) savepoint na conexão da transação: no PostgreSQL um erro deixa a transação inteira
//     "aborted" e nada mais grava. Voltando pro savepoint, o Tratamento ainda consegue
//     gravar o log da Quarentena e mudar o estado da carga (mesma ideia da Tarefa 12)
//  2) traduz o erro do banco em FalhaGeoespacialException (422), em vez de virar 500
// Não usa Propagation.NESTED pelo mesmo motivo do CalculoAnaliticoService: com JPA o Spring
// recusa savepoint ("JpaDialect does not support savepoints"), então é feito na mão
final class ExecucaoGeoespacial {

    private static final Logger log = LoggerFactory.getLogger(ExecucaoGeoespacial.class);

    @FunctionalInterface
    interface Tarefa<T, E extends Exception> {
        T executar() throws E;
    }

    private ExecucaoGeoespacial() {}

    static <T, E extends Exception> T executar(JdbcTemplate jdbcTemplate, String operacao, Tarefa<T, E> tarefa) throws E {
        Connection conexao = conexaoDaTransacao(jdbcTemplate);
        Savepoint savepoint = criarSavepoint(conexao);

        try {
            T resultado = tarefa.executar();
            liberarSavepoint(conexao, savepoint);
            return resultado;

        } catch (Exception e) {
            voltarSavepoint(conexao, savepoint);
            throw ExecucaoGeoespacial.<E>traduzir(operacao, e);
        }
    }

    // erro do PostGIS vira 422; qualquer outra coisa sobe do jeito que veio
    @SuppressWarnings("unchecked")
    private static <E extends Exception> E traduzir(String operacao, Exception e) {
        if (e instanceof FalhaGeoespacialException) {
            return (E) e;
        }
        if (!FalhaGeoespacialException.ehErroGeoespacial(e)) {
            return (E) e;
        }
        FalhaGeoespacialException falha = new FalhaGeoespacialException(operacao, e);
        log.warn("[{}] {}", falha.getCodigoErro(), falha.getReason());
        return (E) falha;
    }

    // só faz savepoint se tiver transação aberta com conexão já presa nela;
    // fora de transação (autocommit) cada comando já é atômico e não precisa
    private static Connection conexaoDaTransacao(JdbcTemplate jdbcTemplate) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null || !TransactionSynchronizationManager.isActualTransactionActive()) {
            return null;
        }
        Connection conexao = DataSourceUtils.getConnection(dataSource);
        try {
            if (conexao.getAutoCommit()) {
                DataSourceUtils.releaseConnection(conexao, dataSource);
                return null;
            }
        } catch (SQLException e) {
            DataSourceUtils.releaseConnection(conexao, dataSource);
            return null;
        }
        // a conexão é da transação: o releaseConnection só devolve a referência, não fecha
        DataSourceUtils.releaseConnection(conexao, dataSource);
        return conexao;
    }

    private static Savepoint criarSavepoint(Connection conexao) {
        if (conexao == null) {
            return null;
        }
        try {
            return conexao.setSavepoint();
        } catch (SQLException e) {
            log.warn("Não foi possível criar savepoint para a operação geoespacial: {}", e.getMessage());
            return null;
        }
    }

    private static void liberarSavepoint(Connection conexao, Savepoint savepoint) {
        if (savepoint == null) {
            return;
        }
        try {
            conexao.releaseSavepoint(savepoint);
        } catch (SQLException e) {
            log.warn("Não foi possível liberar o savepoint geoespacial: {}", e.getMessage());
        }
    }

    private static void voltarSavepoint(Connection conexao, Savepoint savepoint) {
        if (savepoint == null) {
            return;
        }
        try {
            conexao.rollback(savepoint);
        } catch (SQLException e) {
            log.error("Não foi possível voltar ao savepoint geoespacial: {}", e.getMessage());
        }
    }
}
