package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseRequest;
import com.bifrostconnect.api_geo.dto.CruzamentoAnaliseResponse;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;

class AnaliseCruzamentoServiceTest {

    // finge ser o banco: devolve as bases publicadas e as linhas do resultados_calculos
    private static class BancoFalso extends JdbcTemplate {
        final List<String> sqls = new ArrayList<>();
        final List<Object[]> argumentos = new ArrayList<>();
        List<Map<String, Object>> bases = List.of();
        List<Map<String, Object>> sobreposicoes = List.of();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            sqls.add(sql);
            argumentos.add(args);
            List<Map<String, Object>> linhas = sql.contains("FROM processo p") ? bases : sobreposicoes;

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

    private static Map<String, Object> base(Long processoId, Long arquivoId) {
        Map<String, Object> linha = new HashMap<>();
        linha.put("processo_id", processoId);
        linha.put("arquivo_processado_id", arquivoId);
        linha.put("versao_oficial", "1.0");
        linha.put("conjunto", "Conjunto Teste");
        linha.put("orgao", "OT");
        linha.put("ano_safra", "2025");
        return linha;
    }

    private static Map<String, Object> sobreposicao(Long arquivoId, String imovel, String area,
            String fonte, String alerta, String hectares) {
        return Map.of("arquivo_processado_id", arquivoId, "codigo_imovel", imovel,
                "area_hectares", new BigDecimal(area), "fonte_camada", fonte,
                "codigo_alerta_app", alerta, "hectares_sobrepostos", new BigDecimal(hectares));
    }

    private static CruzamentoAnaliseRequest filtro(List<Long> processoIds, List<Categoria> categorias, List<String> fontes) {
        return new CruzamentoAnaliseRequest(processoIds, null, null, null, categorias, fontes);
    }

    private static CalculoAnaliticoService calculo(BancoFalso banco) {
        return new CalculoAnaliticoService(banco, List.of("INCAR_APP"), List.of("INCAR_RESERVA_LEGAL"),
                List.of("INPE_DESMATAMENTO_CERRADO", "IBAMA_EMBARGO"));
    }

    @Test
    void agrupaPropriedadesESomaHectaresAfetados() {
        BancoFalso banco = new BancoFalso();
        banco.bases = List.of(base(7L, 42L), base(8L, 50L));
        banco.sobreposicoes = List.of(
                sobreposicao(42L, "CAR-1", "10.0000", "INCAR_APP", "APP-9", "1.0000"),
                sobreposicao(42L, "CAR-1", "10.0000", "IBAMA_EMBARGO", "E-1", "0.5000"),
                sobreposicao(50L, "CAR-9", "4.0000", "IBAMA_EMBARGO", "E-2", "0.2500"));
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        CruzamentoAnaliseResponse resposta = service.cruzar(filtro(null, null, null));

        assertEquals(2, resposta.basesCruzadas());
        assertEquals(2, resposta.propriedadesAfetadas());
        assertEquals(new BigDecimal("1.7500"), resposta.hectaresAfetados());

        CruzamentoAnaliseResponse.PropriedadeAfetada car1 = resposta.propriedades().get(0);
        assertEquals(7L, car1.processoId());
        assertEquals("CAR-1", car1.codigoImovel());
        assertEquals(new BigDecimal("10.0000"), car1.areaImovelHectares());
        assertEquals(new BigDecimal("1.5000"), car1.hectaresAfetados());
        assertEquals(2, car1.sobreposicoes().size());
        assertEquals(8L, resposta.propriedades().get(1).processoId());

        assertEquals(new BigDecimal("0.7500"), resposta.totaisPorCategoria().get(Categoria.ALERTA).hectares());
        assertEquals(2, resposta.totaisPorCategoria().get(Categoria.ALERTA).quantidade());
        assertEquals(0, resposta.totaisPorCategoria().get(Categoria.RESERVA_LEGAL).quantidade());
    }

    @Test
    void semFiltroDeCamadaCruzaTodasAsFontesConfiguradas() {
        BancoFalso banco = new BancoFalso();
        banco.bases = List.of(base(7L, 42L));
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        CruzamentoAnaliseResponse resposta = service.cruzar(filtro(null, null, null));

        assertEquals(List.of("INCAR_APP", "INCAR_RESERVA_LEGAL", "INPE_DESMATAMENTO_CERRADO", "IBAMA_EMBARGO"),
                resposta.fontesCruzadas());
        assertTrue(banco.sqls.get(1).contains("rc.arquivo_processado_id IN (?)"));
        assertTrue(banco.sqls.get(1).contains("rc.fonte_camada IN (?, ?, ?, ?)"));
        // filtra pela cópia publicada, não pelo processo
        assertEquals(42L, banco.argumentos.get(1)[0]);
    }

    @Test
    void filtraPorCategoria() {
        BancoFalso banco = new BancoFalso();
        banco.bases = List.of(base(7L, 42L));
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        CruzamentoAnaliseResponse resposta = service.cruzar(filtro(null, List.of(Categoria.ALERTA), null));

        assertEquals(List.of("INPE_DESMATAMENTO_CERRADO", "IBAMA_EMBARGO"), resposta.fontesCruzadas());
    }

    @Test
    void fonteInformadaTemPrioridadeECategoriaPodeFicarNula() {
        BancoFalso banco = new BancoFalso();
        banco.bases = List.of(base(7L, 42L));
        banco.sobreposicoes = List.of(
                sobreposicao(42L, "CAR-1", "10.0000", "FUNAI_TERRA_INDIGENA", "TI-1", "2.0000"));
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        CruzamentoAnaliseResponse resposta = service.cruzar(
                filtro(null, List.of(Categoria.APP), List.of(" FUNAI_TERRA_INDIGENA ", "")));

        assertEquals(List.of("FUNAI_TERRA_INDIGENA"), resposta.fontesCruzadas());
        assertNull(resposta.propriedades().get(0).sobreposicoes().get(0).categoria());
    }

    @Test
    void filtrosDaTelaViramCondicoesDoSql() {
        BancoFalso banco = new BancoFalso();
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        service.cruzar(new CruzamentoAnaliseRequest(null, 1L, " 2025 ", 3L, null, null));

        String sql = banco.sqls.get(0);
        assertTrue(sql.contains("p.conjunto_id = ?"));
        assertTrue(sql.contains("p.ano_safra = ?"));
        assertTrue(sql.contains("p.orgao_id = ?"));
        assertEquals(List.of(1L, "2025", 3L), List.of(banco.argumentos.get(0)));
    }

    @Test
    void semBasePublicadaNaoConsultaOCache() {
        BancoFalso banco = new BancoFalso();
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        CruzamentoAnaliseResponse resposta = service.cruzar(filtro(null, null, null));

        assertEquals(1, banco.sqls.size());
        assertEquals(0, resposta.basesCruzadas());
        assertEquals(BigDecimal.ZERO, resposta.hectaresAfetados());
        assertTrue(resposta.propriedades().isEmpty());
    }

    @Test
    void cargaPedidaQueNaoEstaPublicadaDa422() {
        BancoFalso banco = new BancoFalso();
        banco.bases = List.of(base(7L, 42L));
        AnaliseCruzamentoService service = new AnaliseCruzamentoService(banco, calculo(banco));

        ResponseStatusException erro = assertThrows(ResponseStatusException.class,
                () -> service.cruzar(filtro(List.of(7L, 99L), null, null)));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, erro.getStatusCode());
        assertTrue(erro.getReason().contains("99"));
    }
}
