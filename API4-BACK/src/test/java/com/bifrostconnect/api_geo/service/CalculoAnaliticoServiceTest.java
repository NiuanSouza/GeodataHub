package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.CalculosCargaResponse;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;
import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.entity.Processo;

class CalculoAnaliticoServiceTest {

    // finge ser o banco: devolve as áreas e as linhas que o RETURNING traria
    private static class BancoFalso extends JdbcTemplate {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> argumentos = new ArrayList<>();
        List<Map<String, Object>> areas = List.of();
        List<Map<String, Object>> cruzamentos = List.of();
        // só usados na consulta da Tarefa 13
        List<Map<String, Object>> processos = List.of();
        List<Map<String, Object>> copias = List.of();
        // Tarefa 15: se preenchido, a consulta de área estoura como se fosse o PostGIS
        RuntimeException erroNaArea;

        // o savepoint do cálculo pede a conexão; aqui qualquer uma serve
        @Override
        public <T> T execute(ConnectionCallback<T> action) {
            try {
                return action.doInConnection(Mockito.mock(Connection.class));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            sqls.add(sql);
            argumentos.add(args);
            if (erroNaArea != null && sql.contains("ST_Area(geom::geography)")) {
                throw erroNaArea;
            }
            List<Map<String, Object>> linhas;
            if (sql.contains("FROM processo p")) {
                linhas = processos;
            } else if (sql.contains("FROM vw_copia_vigente cv")) {
                linhas = copias;
            } else if (sql.contains("ST_Area(geom::geography)")) {
                linhas = areas;
            } else {
                linhas = cruzamentos;
            }

            List<T> resultado = new ArrayList<>();
            try {
                for (int i = 0; i < linhas.size(); i++) {
                    Map<String, Object> linha = linhas.get(i);
                    ResultSet rs = Mockito.mock(ResultSet.class);
                    Mockito.when(rs.getBigDecimal(Mockito.anyString()))
                            .thenAnswer(inv -> (BigDecimal) linha.get(inv.getArgument(0, String.class)));
                    Mockito.when(rs.getString(Mockito.anyString()))
                            .thenAnswer(inv -> (String) linha.get(inv.getArgument(0, String.class)));
                    Mockito.when(rs.getLong(Mockito.anyString()))
                            .thenAnswer(inv -> (Long) linha.get(inv.getArgument(0, String.class)));
                    resultado.add(rowMapper.mapRow(rs, i));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return resultado;
        }
    }

    private static Map<String, Object> area(String imovel, String hectares) {
        return Map.of("codigo_imovel", imovel, "hectares", new BigDecimal(hectares));
    }

    private static Map<String, Object> cruzamento(String imovel, String fonte, String alerta, String hectares) {
        return Map.of("codigo_imovel", imovel, "fonte_camada", fonte,
                "codigo_alerta_app", alerta, "hectares_sobrepostos", new BigDecimal(hectares));
    }

    private static Processo processo() {
        Processo processo = new Processo();
        processo.setId(7L);
        return processo;
    }

    private static ArquivoProcessado copia() {
        ArquivoProcessado copia = new ArquivoProcessado();
        copia.setId(42L);
        return copia;
    }

    @Test
    void somaAreaECruzamentosPorCategoria() {
        BancoFalso banco = new BancoFalso();
        banco.areas = List.of(area("CAR-1", "10.5000"), area("CAR-2", "2.2500"));
        banco.cruzamentos = List.of(
                cruzamento("CAR-1", "INCAR_APP", "APP-9", "1.0000"),
                cruzamento("CAR-1", "INPE_DESMATAMENTO_CERRADO", "D-1", "0.5000"),
                cruzamento("CAR-2", "IBAMA_EMBARGO", "E-3", "0.2500"));

        CalculoAnaliticoService service = new CalculoAnaliticoService(banco,
                List.of("INCAR_APP"), List.of("INCAR_RESERVA_LEGAL"),
                List.of("INPE_DESMATAMENTO_CERRADO", " IBAMA_EMBARGO "));

        ResultadoCalculoAnalitico resultado = service.calcular(processo(), copia());

        assertEquals(2, resultado.poligonosCalculados());
        assertEquals(new BigDecimal("12.7500"), resultado.areaTotalHectares());
        assertEquals(new BigDecimal("1.0000"), resultado.hectaresPorCategoria(Categoria.APP));
        assertEquals(BigDecimal.ZERO, resultado.hectaresPorCategoria(Categoria.RESERVA_LEGAL));
        assertEquals(new BigDecimal("0.7500"), resultado.hectaresPorCategoria(Categoria.ALERTA));
        assertEquals(2, resultado.contar(Categoria.ALERTA));
        assertFalse(resultado.possuiInconsistencias());
    }

    @Test
    void passaAsFontesConfiguradasComoParametro() {
        BancoFalso banco = new BancoFalso();
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco,
                List.of("INCAR_APP"), List.of(""), List.of("IBAMA_EMBARGO"));

        service.calcular(processo(), copia());

        String sqlCruzamento = banco.sqls.get(1);
        assertTrue(sqlCruzamento.contains("cr.fonte IN (?, ?)"));
        assertEquals(List.of(7L, 42L, "INCAR_APP", "IBAMA_EMBARGO", 42L,
                        ValidacaoTopologicaService.TOLERANCIA_SOBREPOSICAO_HA),
                List.of(banco.argumentos.get(1)));
    }

    @Test
    void semFonteConfiguradaSoCalculaArea() {
        BancoFalso banco = new BancoFalso();
        banco.areas = List.of(area("CAR-1", "3.0000"));
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco, List.of(""), List.of(), List.of());

        ResultadoCalculoAnalitico resultado = service.calcular(processo(), copia());

        assertEquals(1, banco.sqls.size());
        assertFalse(banco.sqls.get(0).contains("resultados_calculos"));
        assertTrue(resultado.cruzamentos().isEmpty());
        assertEquals(new BigDecimal("3.0000"), resultado.areaTotalHectares());
    }

    @Test
    void poligonoComAreaZeradaViraInconsistencia() {
        BancoFalso banco = new BancoFalso();
        banco.areas = List.of(area("CAR-1", "5.0000"), area("CAR-2", "0.0000"));
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco, List.of(), List.of(), List.of());

        ResultadoCalculoAnalitico resultado = service.calcular(processo(), copia());

        assertTrue(resultado.possuiInconsistencias());
        assertEquals(1, resultado.inconsistencias().size());
        assertTrue(resultado.inconsistencias().get(0).contains("CAR-2"));
    }

    @Test
    void cruzamentoMaiorQueOImovelViraInconsistencia() {
        BancoFalso banco = new BancoFalso();
        banco.areas = List.of(area("CAR-1", "2.0000"));
        banco.cruzamentos = List.of(
                cruzamento("CAR-1", "INCAR_APP", "APP-1", "2.0001"),
                cruzamento("CAR-1", "IBAMA_EMBARGO", "E-1", "2.5000"));
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco,
                List.of("INCAR_APP"), List.of(), List.of("IBAMA_EMBARGO"));

        ResultadoCalculoAnalitico resultado = service.calcular(processo(), copia());

        // 2.0001 tá dentro da tolerância de arredondamento, só o 2.5 é erro de verdade
        assertEquals(1, resultado.inconsistencias().size());
        assertTrue(resultado.inconsistencias().get(0).contains("E-1"));
    }

    // ---- Tarefa 13: consulta dos cálculos ----

    private static Map<String, Object> processoEm(String etapa, String situacao) {
        return Map.of("etapa", etapa, "situacao", situacao);
    }

    @Test
    void consultaMontaAreasIntersecoesEAlertas() {
        BancoFalso banco = new BancoFalso();
        banco.processos = List.of(processoEm("CALCULO_ANALITICO", "CONCLUIDA"));
        banco.copias = List.of(Map.of("id", 42L));
        banco.areas = List.of(area("CAR-1", "10.5000"), area("CAR-2", "2.2500"));
        banco.cruzamentos = List.of(
                cruzamento("CAR-1", "INCAR_APP", "APP-9", "1.0000"),
                cruzamento("CAR-1", "INPE_DESMATAMENTO_CERRADO", "D-1", "0.5000"),
                cruzamento("CAR-2", "IBAMA_EMBARGO", "E-3", "0.2500"));
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco,
                List.of("INCAR_APP"), List.of("INCAR_RESERVA_LEGAL"),
                List.of("INPE_DESMATAMENTO_CERRADO", "IBAMA_EMBARGO"));

        CalculosCargaResponse resposta = service.consultar(7L);

        assertEquals(7L, resposta.processoId());
        assertEquals(42L, resposta.arquivoProcessadoId());
        assertEquals("CONCLUIDA", resposta.situacao());
        assertEquals(2, resposta.poligonosCalculados());
        assertEquals(new BigDecimal("12.7500"), resposta.areaTotalHectares());
        assertEquals("CAR-2", resposta.poligonos().get(1).codigoImovel());
        assertEquals(3, resposta.intersecoes().size());
        assertEquals(2, resposta.alertasAmbientais().size());
        assertEquals(new BigDecimal("0.7500"), resposta.totaisPorCategoria().get(Categoria.ALERTA).hectares());
        assertEquals(1, resposta.totaisPorCategoria().get(Categoria.APP).quantidade());
        // categoria sem cruzamento aparece zerada pra tela não ter que tratar ausência
        assertEquals(0, resposta.totaisPorCategoria().get(Categoria.RESERVA_LEGAL).quantidade());
        // as consultas são pela cópia de trabalho achada, não pelo processo
        assertEquals(List.of(42L), List.of(banco.argumentos.get(2)));
    }

    @Test
    void consultaSemCalculoVoltaVazia() {
        BancoFalso banco = new BancoFalso();
        banco.processos = List.of(processoEm("TRATAMENTO", "EM_VALIDACAO"));
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco, List.of(), List.of(), List.of());

        CalculosCargaResponse resposta = service.consultar(7L);

        assertNull(resposta.arquivoProcessadoId());
        assertEquals("EM_VALIDACAO", resposta.situacao());
        assertEquals(BigDecimal.ZERO, resposta.areaTotalHectares());
        assertTrue(resposta.poligonos().isEmpty());
        assertTrue(resposta.intersecoes().isEmpty());
        assertEquals(2, banco.sqls.size());
    }

    @Test
    void consultaDeCargaInexistenteDa404() {
        CalculoAnaliticoService service = new CalculoAnaliticoService(new BancoFalso(), List.of(), List.of(), List.of());

        ResponseStatusException erro = assertThrows(ResponseStatusException.class, () -> service.consultar(99L));

        assertEquals(HttpStatus.NOT_FOUND, erro.getStatusCode());
    }

    // ---- Tarefa 15: erro do PostGIS no meio da conta ----

    private static RuntimeException divisaoPorZero() {
        return new org.springframework.jdbc.UncategorizedSQLException("ST_Area", "SELECT ...",
                new java.sql.SQLException("ERROR: division by zero", "22012"));
    }

    @Test
    void divisaoPorZeroNoCalculoVira422() {
        BancoFalso banco = new BancoFalso();
        banco.erroNaArea = divisaoPorZero();
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco,
                List.of("INCAR_APP"), List.of(), List.of());

        com.bifrostconnect.api_geo.exception.FalhaGeoespacialException falha = assertThrows(
                com.bifrostconnect.api_geo.exception.FalhaGeoespacialException.class,
                () -> service.calcular(processo(), copia()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, falha.getStatusCode());
        assertEquals("GEO_DIVISAO_POR_ZERO", falha.getCodigoErro());
        assertEquals("cálculo de área e cruzamentos espaciais", falha.getOperacao());
    }

    @Test
    void divisaoPorZeroNaConsultaTambemVira422() {
        BancoFalso banco = new BancoFalso();
        banco.processos = List.of(Map.of("etapa", "TRATAMENTO", "situacao", "CONCLUIDA"));
        banco.copias = List.of(Map.of("id", 42L));
        banco.erroNaArea = divisaoPorZero();
        CalculoAnaliticoService service = new CalculoAnaliticoService(banco, List.of(), List.of(), List.of());

        ResponseStatusException falha = assertThrows(ResponseStatusException.class, () -> service.consultar(7L));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, falha.getStatusCode());
    }
}
