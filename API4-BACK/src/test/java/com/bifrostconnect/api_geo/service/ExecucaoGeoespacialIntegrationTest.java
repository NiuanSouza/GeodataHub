package com.bifrostconnect.api_geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.bifrostconnect.api_geo.exception.FalhaGeoespacialException;

// Tarefa 15 com o PostGIS de verdade: os erros aqui são os que o banco solta mesmo.
// Roda em transação com rollback, igual o DadosPublicadosMapaIntegrationTest
@SpringBootTest
@Transactional
class ExecucaoGeoespacialIntegrationTest {

    // linha não tem área: ST_Area dá 0 e a divisão estoura
    private static final String SQL_DIVISAO_POR_ZERO = """
            SELECT ST_Area(geom::geography) / ST_Area(geom)
              FROM (SELECT ST_GeomFromText('LINESTRING(-47 -15, -47.01 -15.01)', 4674) AS geom) s
            """;

    // coordenada absurda na policônica do SIRGAS: o PROJ não consegue projetar
    private static final String SQL_ERRO_PROJECAO =
            "SELECT ST_AsText(ST_Transform(ST_SetSRID(ST_MakePoint(1e30, 0), 5880), 4674))";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PadronizacaoEpsgService padronizacaoEpsgService;

    @Test
    void divisaoPorZeroNoPostgisVira422() {
        FalhaGeoespacialException falha = assertThrows(FalhaGeoespacialException.class, () ->
                ExecucaoGeoespacial.executar(jdbc, "cálculo de área",
                        () -> jdbc.queryForObject(SQL_DIVISAO_POR_ZERO, Double.class)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, falha.getStatusCode());
        assertEquals(FalhaGeoespacialException.Tipo.DIVISAO_POR_ZERO, falha.getTipo());
        assertThat(falha.getDetalheTecnico()).contains("division by zero");
    }

    @Test
    void erroDeProjecaoNoPostgisVira422() {
        FalhaGeoespacialException falha = assertThrows(FalhaGeoespacialException.class, () ->
                ExecucaoGeoespacial.executar(jdbc, "conversão de coordenadas",
                        () -> jdbc.queryForObject(SQL_ERRO_PROJECAO, String.class)));

        assertEquals(FalhaGeoespacialException.Tipo.PROJECAO, falha.getTipo());
        assertEquals("GEO_ERRO_PROJECAO", falha.getCodigoErro());
    }

    // o ponto principal do savepoint: depois do erro a transação continua gravando,
    // senão o Tratamento não conseguiria escrever o log da Quarentena
    @Test
    void transacaoContinuaUsavelDepoisDoErro() {
        assertThrows(FalhaGeoespacialException.class, () ->
                ExecucaoGeoespacial.executar(jdbc, "cálculo de área",
                        () -> jdbc.queryForObject(SQL_DIVISAO_POR_ZERO, Double.class)));

        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
        assertThat(jdbc.queryForObject("SELECT ST_Area(ST_GeomFromText('POLYGON((0 0,1 0,1 1,0 1,0 0))'))",
                Double.class)).isEqualTo(1.0);
    }

    @Test
    void desfazSoOQueAOperacaoQueFalhouGravou() {
        jdbc.execute("CREATE TEMP TABLE t15_teste (valor INT) ON COMMIT DROP");
        jdbc.update("INSERT INTO t15_teste VALUES (1)");

        assertThrows(FalhaGeoespacialException.class, () ->
                ExecucaoGeoespacial.executar(jdbc, "cálculo", () -> {
                    jdbc.update("INSERT INTO t15_teste VALUES (2)");
                    return jdbc.queryForObject(SQL_DIVISAO_POR_ZERO, Double.class);
                }));

        // o 1 (antes do savepoint) fica, o 2 (dentro da operação que estourou) some
        assertThat(jdbc.queryForList("SELECT valor FROM t15_teste ORDER BY valor", Integer.class))
                .containsExactly(1);
    }

    @Test
    void conversaoDeCoordenadasComErroDeProjecaoNaoDeixaArquivoPelaMetade(@TempDir Path pasta) throws Exception {
        Path origem = pasta.resolve("policonica.geojson");
        Files.writeString(origem, """
                {"type":"FeatureCollection",
                 "crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::5880"}},
                 "features":[{"type":"Feature","properties":{"cod_imovel":"CAR-T15"},
                   "geometry":{"type":"Polygon","coordinates":[[[1e30,0],[1e30,1],[2e30,1],[1e30,0]]]}}]}
                """);
        Path destino = pasta.resolve("convertido.geojson");

        FalhaGeoespacialException falha = assertThrows(FalhaGeoespacialException.class, () ->
                padronizacaoEpsgService.converterParaEpsgOficial(origem, "5880", destino));

        assertEquals(FalhaGeoespacialException.Tipo.PROJECAO, falha.getTipo());
        assertThat(falha.getOperacao()).contains("EPSG:5880");
        assertThat(destino).doesNotExist();
    }
}
