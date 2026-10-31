package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.Problema;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.TipoProblema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class FeicaoCargaServiceTest {

    @TempDir Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String QUADRADO =
            "{\"type\":\"Polygon\",\"coordinates\":[[[-46.05,-15],[-46.04,-15],[-46.04,-15.01],[-46.05,-15.01],[-46.05,-15]]]}";

    // finge ser o banco: só guarda o que ia ser inserido na feicao_carga
    private static class BancoFalso extends JdbcTemplate {
        final List<Object[]> inseridas = new ArrayList<>();
        final List<Integer> tamanhoDosLotes = new ArrayList<>();

        @Override
        public int[] batchUpdate(String sql, List<Object[]> argumentos) {
            inseridas.addAll(argumentos);
            tamanhoDosLotes.add(argumentos.size());
            return new int[argumentos.size()];
        }
    }

    private JsonNode json(String texto) throws Exception {
        return mapper.readTree(texto);
    }

    private Path geojson(String... features) throws Exception {
        Path arquivo = tempDir.resolve("copia.geojson");
        Files.writeString(arquivo, "{\"type\":\"FeatureCollection\",\"crs\":{\"type\":\"name\"},\"features\":["
                + String.join(",", features) + "]}");
        return arquivo;
    }

    private static String feicao(String codigo, String geometria) {
        return "{\"type\":\"Feature\",\"properties\":{\"id_propriedade\":\"" + codigo + "\"},\"geometry\":" + geometria + "}";
    }

    @Test
    void poligonoFechadoEhAceito() throws Exception {
        assertNull(FeicaoCargaService.verificarGeometria(json(QUADRADO)));
    }

    @Test
    void anelAbertoEhRejeitado() throws Exception {
        String aberto = "{\"type\":\"Polygon\",\"coordinates\":[[[-46.05,-15],[-46.04,-15],[-46.04,-15.01],[-46.05,-15.01]]]}";
        String motivo = FeicaoCargaService.verificarGeometria(json(aberto));
        assertNotNull(motivo);
        assertTrue(motivo.contains("não está fechado") || motivo.contains("menos de 4"), motivo);

        String abertoCom4 = "{\"type\":\"Polygon\",\"coordinates\":[[[-46.05,-15],[-46.04,-15],[-46.04,-15.01],[-46.05,-15.01],[-46.06,-15]]]}";
        assertTrue(FeicaoCargaService.verificarGeometria(json(abertoCom4)).contains("não está fechado"));
    }

    @Test
    void linhaESemGeometriaSaoRejeitadas() throws Exception {
        assertTrue(FeicaoCargaService.verificarGeometria(
                json("{\"type\":\"LineString\",\"coordinates\":[[0,0],[1,1]]}")).contains("não é um polígono"));
        assertEquals("Feição sem geometria.", FeicaoCargaService.verificarGeometria(null));
    }

    @Test
    void multiPolygonComParteAbertaEhRejeitado() throws Exception {
        String multi = "{\"type\":\"MultiPolygon\",\"coordinates\":["
                + "[[[-46.05,-15],[-46.04,-15],[-46.04,-15.01],[-46.05,-15]]],"
                + "[[[-46.05,-15],[-46.04,-15],[-46.04,-15.01],[-46.05,-15.01],[-46.06,-15]]]]}";
        assertTrue(FeicaoCargaService.verificarGeometria(json(multi)).contains("(parte 2)"));
    }

    @Test
    void coordenadaForaDoSirgasEhRejeitada() throws Exception {
        // coordenada em metros (UTM), como se tivesse escapado da reprojeção
        String utm = "{\"type\":\"Polygon\",\"coordinates\":[[[500000,8000000],[500100,8000000],[500100,8000100],[500000,8000000]]]}";
        assertTrue(FeicaoCargaService.verificarGeometria(json(utm)).contains("fora da faixa"));
    }

    @Test
    void codigoDoImovelVemDasPropriedadesOuDaPosicao() throws Exception {
        assertEquals("MS-123", FeicaoCargaService.extrairCodigoImovel(
                json("{\"properties\":{\"COD_IMOVEL\":\" MS-123 \"}}"), 1));
        assertEquals("4106902", FeicaoCargaService.extrairCodigoImovel(
                json("{\"properties\":{\"CD_MUN\":\"4106902\",\"fid\":\"1\"}}"), 2));
        assertEquals("TI-40101", FeicaoCargaService.extrairCodigoImovel(
                json("{\"properties\":{\"terrai_cod\":\"TI-40101\"}}"), 3));
        assertEquals("FEICAO_7", FeicaoCargaService.extrairCodigoImovel(
                json("{\"properties\":{\"nome\":\"Fazenda\"}}"), 7));
    }

    @Test
    void carregaValidasERejeitaAbertasECodigosRepetidos() throws Exception {
        String aberto = "{\"type\":\"Polygon\",\"coordinates\":[[[-46.05,-15],[-46.04,-15],[-46.04,-15.01],[-46.05,-15.01],[-46.06,-15]]]}";
        Path arquivo = geojson(
                feicao("A", QUADRADO),
                feicao("B", aberto),
                feicao("A", QUADRADO),
                feicao("C", QUADRADO));

        BancoFalso banco = new BancoFalso();
        FeicaoCargaService.ResultadoCarga resultado = new FeicaoCargaService(banco).carregar(42L, arquivo);

        assertEquals(4, resultado.totalFeicoes());
        assertEquals(2, resultado.carregadas());
        assertEquals(List.of("A", "C"), banco.inseridas.stream().map(a -> (String) a[1]).toList());
        assertEquals(42L, banco.inseridas.get(0)[0]);

        List<Problema> rejeitadas = resultado.rejeitadas();
        assertEquals(2, rejeitadas.size());
        assertEquals(TipoProblema.GEOMETRIA_INVALIDA, rejeitadas.get(0).tipo());
        assertEquals("B", rejeitadas.get(0).codigoImovel());
        assertEquals(TipoProblema.CODIGO_IMOVEL_DUPLICADO, rejeitadas.get(1).tipo());
        assertEquals("A", rejeitadas.get(1).codigoImovel());
    }

    @Test
    void insereEmLotes() throws Exception {
        String[] features = new String[1201];
        for (int i = 0; i < features.length; i++) {
            features[i] = feicao("IMOVEL-" + i, QUADRADO);
        }
        BancoFalso banco = new BancoFalso();
        FeicaoCargaService.ResultadoCarga resultado = new FeicaoCargaService(banco).carregar(1L, geojson(features));

        assertEquals(1201, resultado.carregadas());
        assertEquals(List.of(500, 500, 201), banco.tamanhoDosLotes);
    }
}
