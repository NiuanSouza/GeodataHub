package com.bifrostconnect.api_geo.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.exception.FalhaGeoespacialException.Tipo;

class FalhaGeoespacialExceptionTest {

    // do jeito que o Spring entrega: o SQLException do driver embrulhado
    private static UncategorizedSQLException doPostgis(String sqlState, String mensagem) {
        return new UncategorizedSQLException("teste", "SELECT ...", new SQLException(mensagem, sqlState));
    }

    @Test
    void divisaoPorZeroPeloSqlState() {
        assertEquals(Tipo.DIVISAO_POR_ZERO,
                FalhaGeoespacialException.classificar(doPostgis("22012", "ERROR: division by zero")));
    }

    @Test
    void divisaoPorZeroDoJava() {
        assertEquals(Tipo.DIVISAO_POR_ZERO,
                FalhaGeoespacialException.classificar(new ArithmeticException("/ by zero")));
    }

    @Test
    void erroDeProjecaoDoProj() {
        assertEquals(Tipo.PROJECAO, FalhaGeoespacialException.classificar(
                doPostgis("XX000", "ERROR: transform: tolerance condition error (-20)")));
        assertEquals(Tipo.PROJECAO, FalhaGeoespacialException.classificar(
                doPostgis("XX000", "ERROR: Cannot find SRID (999999) in spatial_ref_sys")));
    }

    @Test
    void erroTopologicoDoGeos() {
        assertEquals(Tipo.TOPOLOGIA, FalhaGeoespacialException.classificar(doPostgis("XX000",
                "ERROR: GEOSIntersection: TopologyException: Input geom 1 is invalid: Self-intersection")));
    }

    @Test
    void outroErroDoBancoViraErroGeometrico() {
        assertEquals(Tipo.GEOMETRIA, FalhaGeoespacialException.classificar(
                doPostgis("XX000", "ERROR: lwgeom_area_spher: unsupported type")));
    }

    @Test
    void saiComo422ComCodigoEOperacao() {
        FalhaGeoespacialException falha = new FalhaGeoespacialException(
                "conversão de coordenadas", doPostgis("XX000", "ERROR: transform: tolerance condition error (-20)"));

        assertTrue(falha instanceof ResponseStatusException);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, falha.getStatusCode());
        assertEquals("GEO_ERRO_PROJECAO", falha.getCodigoErro());
        assertEquals("conversão de coordenadas", falha.getOperacao());
        // o "ERROR:" do PostgreSQL sai, fica só o motivo
        assertEquals("transform: tolerance condition error (-20)", falha.getDetalheTecnico());
        assertTrue(falha.getReason().contains("conversão de coordenadas"));
    }

    @Test
    void codigoCabeNaColunaCodigoErro() {
        // log_processamento.codigo_erro é VARCHAR(30)
        for (Tipo tipo : Tipo.values()) {
            assertTrue(tipo.getCodigo().length() <= 30, tipo.getCodigo());
        }
    }

    @Test
    void detalheMuitoGrandeECortado() {
        FalhaGeoespacialException falha = new FalhaGeoespacialException("cálculo", doPostgis("XX000", "x".repeat(2000)));
        assertEquals(503, falha.getDetalheTecnico().length());
    }

    @Test
    void soContaComoGeoespacialOQueVeioDoBancoOuDeConta() {
        assertTrue(FalhaGeoespacialException.ehErroGeoespacial(doPostgis("22012", "division by zero")));
        assertTrue(FalhaGeoespacialException.ehErroGeoespacial(new RuntimeException(new SQLException("x"))));
        assertTrue(FalhaGeoespacialException.ehErroGeoespacial(new ArithmeticException()));
        assertFalse(FalhaGeoespacialException.ehErroGeoespacial(new IOException("arquivo sumiu")));
        assertFalse(FalhaGeoespacialException.ehErroGeoespacial(new IllegalArgumentException("EPSG inválido")));
    }
}
