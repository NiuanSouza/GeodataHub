package com.bifrostconnect.api_geo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

class PadronizacaoEpsgServiceTest {

    @TempDir Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();

    private static class PostgisFalso extends JdbcTemplate {
        final List<Integer> sridsRecebidos = new ArrayList<>();
        final List<Integer> tamanhoDosLotes = new ArrayList<>();
        String sqlRecebido;
        boolean falhar;
        private final ObjectMapper om = new ObjectMapper();

        @Override
        @SuppressWarnings("unchecked")
        public <T> T execute(ConnectionCallback<T> action) {
            if (falhar) {
                throw new IllegalStateException("geometria inválida simulada");
            }
            Connection conexao = (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[] { Connection.class }, (p, m, args) -> {
                        switch (m.getName()) {
                            case "createArrayOf":
                                Object[] elementos = (Object[]) args[1];
                                return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Array.class },
                                        (p2, m2, a2) -> m2.getName().equals("getArray") ? elementos : null);
                            case "prepareStatement":
                                sqlRecebido = (String) args[0];
                                return statement();
                            default:
                                return null;
                        }
                    });
            try {
                return action.doInConnection(conexao);
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }

        private PreparedStatement statement() {
            final Object[] array = new Object[1];
            return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[] { PreparedStatement.class }, (p, m, args) -> {
                        switch (m.getName()) {
                            case "setInt":
                                sridsRecebidos.add((Integer) args[1]);
                                return null;
                            case "setArray":
                                array[0] = ((Array) args[1]).getArray();
                                return null;
                            case "executeQuery":
                                Object[] entrada = (Object[]) array[0];
                                tamanhoDosLotes.add(entrada.length);
                                List<String> saida = new ArrayList<>();
                                for (Object g : entrada) {
                                    saida.add(deslocarX(om.readTree((String) g)).toString());
                                }
                                return resultSet(saida);
                            default:
                                return null;
                        }
                    });
        }

        private ResultSet resultSet(List<String> linhas) {
            final int[] i = { -1 };
            return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
                    (InvocationHandler) (p, m, args) -> {
                        switch (m.getName()) {
                            case "next":
                                return ++i[0] < linhas.size();
                            case "getString":
                                return linhas.get(i[0]);
                            default:
                                return null;
                        }
                    });
        }

        private JsonNode deslocarX(JsonNode no) {
            if (no.isArray()) {
                ArrayNode copia = om.createArrayNode();
                boolean ehPonto = no.size() >= 2 && no.get(0).isNumber();
                if (ehPonto) {
                    copia.add(no.get(0).asDouble() + 1000);
                    for (int k = 1; k < no.size(); k++) copia.add(no.get(k));
                } else {
                    no.forEach(filho -> copia.add(deslocarX(filho)));
                }
                return copia;
            }
            if (no.has("coordinates")) {
                var obj = om.createObjectNode();
                obj.put("type", no.get("type").asText());
                obj.set("coordinates", deslocarX(no.get("coordinates")));
                return obj;
            }
            return no;
        }
    }

    private Path gravar(String nome, String conteudo) throws IOException {
        Path arquivo = tempDir.resolve(nome);
        Files.writeString(arquivo, conteudo);
        return arquivo;
    }

    @Test
    void normalizaEpsgDeclarado() {
        PadronizacaoEpsgService service = new PadronizacaoEpsgService(new PostgisFalso());
        assertEquals("4674", service.normalizarEpsg("4674"));
        assertEquals("4674", service.normalizarEpsg(" epsg:4674 "));
        assertThrows(IllegalArgumentException.class, () -> service.normalizarEpsg("  "));
        assertThrows(IllegalArgumentException.class, () -> service.normalizarEpsg(null));
    }

    @Test
    void divergenciaEntreDeclaradoEDetectado() {
        PadronizacaoEpsgService service = new PadronizacaoEpsgService(new PostgisFalso());

        var diverge = service.comparar("4674", "4326");
        assertTrue(diverge.divergente());
        assertFalse(diverge.jaEstaNoPadraoOficial());

        var confere = service.comparar("EPSG:4326", "4326");
        assertFalse(confere.divergente());
        assertFalse(confere.jaEstaNoPadraoOficial());

        assertTrue(service.comparar("4674", "4674").jaEstaNoPadraoOficial());
    }

    @Test
    void converteCopiaParaEpsg4674EmLotesPreservandoDados() throws Exception {
        // 1.201 feições em lotes de 500 => 3 idas ao banco (499 + 500 + 201 com geometria; a 8ª feição não tem).
        StringBuilder feicoes = new StringBuilder();
        for (int i = 0; i < 1201; i++) {
            if (i > 0) feicoes.append(',');
            String geometria = i == 7 ? "null"
                    : "{\"type\":\"Point\",\"coordinates\":[" + i + ".5,-20.25]}";
            feicoes.append("{\"type\":\"Feature\",\"properties\":{\"id\":").append(i)
                    .append(",\"nome\":\"Fazenda ção ").append(i).append("\"},\"geometry\":")
                    .append(geometria).append('}');
        }
        Path origem = gravar("origem.geojson", """
                {"type":"FeatureCollection","name":"meu_layer",
                 "crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::31983"}},
                 "features":[%s]}
                """.formatted(feicoes));
        Path destino = tempDir.resolve("copia.geojson");

        PostgisFalso postgis = new PostgisFalso();
        new PadronizacaoEpsgService(postgis).converterParaEpsgOficial(origem, "31983", destino);

        assertEquals(List.of(499, 500, 201), postgis.tamanhoDosLotes);
        assertTrue(postgis.sridsRecebidos.stream().allMatch(s -> s == 31983));
        assertTrue(postgis.sqlRecebido.contains("ST_Transform"));
        assertTrue(postgis.sqlRecebido.contains("4674"));

        JsonNode saida = mapper.readTree(destino.toFile());
        assertEquals("FeatureCollection", saida.get("type").asText());
        assertEquals("meu_layer", saida.get("name").asText());                      
        assertEquals("urn:ogc:def:crs:EPSG::4674", saida.at("/crs/properties/name").asText());
        assertEquals(1201, saida.get("features").size());

        JsonNode f0 = saida.get("features").get(0);
        assertEquals(1000.5, f0.at("/geometry/coordinates/0").asDouble(), 1e-9);     
        assertEquals(-20.25, f0.at("/geometry/coordinates/1").asDouble(), 1e-9);
        assertEquals("Fazenda ção 0", f0.at("/properties/nome").asText());          
        assertTrue(saida.get("features").get(7).get("geometry").isNull());           
        assertEquals(1200, saida.get("features").get(1200).at("/properties/id").asInt()); 
        assertEquals(2200.5, saida.get("features").get(1200).at("/geometry/coordinates/0").asDouble(), 1e-9);

        
        assertEquals("4674", new GeoJsonEpsgService(new JdbcTemplate()).detectar(destino));
    }

    @Test
    void naoAlteraOArquivoOriginal() throws Exception {
        Path origem = gravar("orig.geojson",
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},"
                        + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}}]}");
        String antes = Files.readString(origem);
        new PadronizacaoEpsgService(new PostgisFalso())
                .converterParaEpsgOficial(origem, "4326", tempDir.resolve("c.geojson"));
        assertEquals(antes, Files.readString(origem));
    }

    @Test
    void arquivoJaEm4674EhCopiadoSemPassarPeloBanco() throws Exception {
        String conteudo = "{\"type\":\"FeatureCollection\",\"crs\":{\"type\":\"name\",\"properties\":"
                + "{\"name\":\"EPSG:4674\"}},\"features\":[]}";
        Path origem = gravar("ja4674.geojson", conteudo);
        Path destino = tempDir.resolve("ja4674-copia.geojson");

        PostgisFalso postgis = new PostgisFalso();
        postgis.falhar = true; 
        new PadronizacaoEpsgService(postgis).converterParaEpsgOficial(origem, "4674", destino);

        assertEquals(conteudo, Files.readString(destino));
    }

    @Test
    void falhaNoBancoNaoDeixaCopiaParcialNoArmazenamento() throws Exception {
        Path origem = gravar("erro.geojson",
                "{\"type\":\"FeatureCollection\",\"features\":[{\"type\":\"Feature\",\"properties\":{},"
                        + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[1,2]}}]}");
        Path destino = tempDir.resolve("erro-copia.geojson");

        PostgisFalso postgis = new PostgisFalso();
        postgis.falhar = true;

        assertThrows(IllegalStateException.class,
                () -> new PadronizacaoEpsgService(postgis).converterParaEpsgOficial(origem, "4326", destino));
        assertFalse(Files.exists(destino));
    }

    @Test
    void calculaSha256() throws Exception {
        Path arquivo = gravar("hash.txt", "abc");
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                new PadronizacaoEpsgService(new PostgisFalso()).calcularSha256(arquivo));
    }
}