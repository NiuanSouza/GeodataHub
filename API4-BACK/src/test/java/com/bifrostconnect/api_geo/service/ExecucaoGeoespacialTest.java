package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bifrostconnect.api_geo.exception.FalhaGeoespacialException;

// sem banco: JdbcTemplate sem DataSource, então não tem savepoint, só a tradução do erro
class ExecucaoGeoespacialTest {

    private final JdbcTemplate semBanco = new JdbcTemplate();

    @Test
    void devolveOResultadoQuandoDaCerto() {
        assertEquals(42, ExecucaoGeoespacial.executar(semBanco, "teste", () -> 42));
    }

    @Test
    void erroDoPostgisVira422() {
        FalhaGeoespacialException falha = assertThrows(FalhaGeoespacialException.class, () ->
                ExecucaoGeoespacial.executar(semBanco, "cálculo de área", () -> {
                    throw new UncategorizedSQLException("teste", "SELECT",
                            new SQLException("ERROR: division by zero", "22012"));
                }));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, falha.getStatusCode());
        assertEquals(FalhaGeoespacialException.Tipo.DIVISAO_POR_ZERO, falha.getTipo());
        assertEquals("cálculo de área", falha.getOperacao());
    }

    @Test
    void erroQueNaoEGeoespacialSobeDoJeitoQueVeio() {
        IllegalStateException original = new IllegalStateException("regra não cadastrada");

        IllegalStateException recebido = assertThrows(IllegalStateException.class, () ->
                ExecucaoGeoespacial.executar(semBanco, "teste", () -> {
                    throw original;
                }));
        assertSame(original, recebido);
    }

    @Test
    void excecaoCheckedTambemPassaReto() {
        assertThrows(IOException.class, () ->
                ExecucaoGeoespacial.executar(semBanco, "teste", () -> {
                    throw new IOException("arquivo sumiu");
                }));
    }

    @Test
    void naoEmbrulhaDuasVezes() {
        FalhaGeoespacialException interna = new FalhaGeoespacialException("conversão",
                new SQLException("ERROR: transform: tolerance condition error (-20)", "XX000"));

        FalhaGeoespacialException recebida = assertThrows(FalhaGeoespacialException.class, () ->
                ExecucaoGeoespacial.executar(semBanco, "validação", () -> {
                    throw interna;
                }));
        assertSame(interna, recebida);
        assertEquals("conversão", recebida.getOperacao());
    }
}
