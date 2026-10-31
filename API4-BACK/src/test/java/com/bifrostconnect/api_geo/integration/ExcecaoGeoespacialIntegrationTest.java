package com.bifrostconnect.api_geo.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

// Tarefa 15 de ponta a ponta: carga em EPSG:5880 com coordenada que o PROJ não consegue projetar.
// O Tratamento tem que capturar o erro do PostGIS, mandar a carga pra Quarentena, gravar o log
// com o código GEO_... e devolver 422, e o servidor continua respondendo normal depois.
// Sem @Transactional: o atualizarEstado roda em REQUIRES_NEW e precisa enxergar o processo gravado
@SpringBootTest
class ExcecaoGeoespacialIntegrationTest {

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private JdbcTemplate jdbc;

    @TempDir
    Path pasta;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    private long criarCargaComErroDeProjecao() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);

        Path geojson = pasta.resolve("t15_" + s + ".geojson");
        Files.writeString(geojson, """
                {"type":"FeatureCollection",
                 "crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::5880"}},
                 "features":[{"type":"Feature","properties":{"cod_imovel":"CAR-T15"},
                   "geometry":{"type":"Polygon","coordinates":[[[1e30,0],[1e30,1],[2e30,1],[1e30,0]]]}}]}
                """);

        Long operador = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash, perfil_id, ativo) "
                        + "VALUES (?, ?, 'hash', (SELECT id FROM perfil WHERE nome = 'ADMINISTRADOR'), TRUE) "
                        + "RETURNING id",
                Long.class, "Operador T15 " + s, "operador.t15." + s + "@teste.local");

        Long processo = jdbc.queryForObject(
                "INSERT INTO processo (operador_id, conjunto_id, orgao_id, ano_safra, epsg_origem) "
                        + "VALUES (?, (SELECT min(id) FROM conjunto), (SELECT min(id) FROM orgao), '2025/2026', '5880') "
                        + "RETURNING id",
                Long.class, operador);

        jdbc.update(
                "INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256) "
                        + "VALUES (?, ?, ?, ?, ?)",
                processo, operador, geojson.getFileName().toString(), geojson.toString(),
                (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""));

        return processo;
    }

    @Test
    void erroDeProjecaoNoTratamentoVaiPraQuarentenaEDevolve422() throws Exception {
        long processoId = criarCargaComErroDeProjecao();

        mockMvc.perform(post("/processos/{id}/processar", processoId).param("nomeArquivo", "carga.geojson"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("GEO_ERRO_PROJECAO"))
                .andExpect(jsonPath("$.quarentena").value(true))
                .andExpect(jsonPath("$.operacao").value("conversão de coordenadas EPSG:5880 -> EPSG:4674"));

        // carga parada na Quarentena da Validação (VALIDACAO / EM_VALIDACAO)
        Map<String, Object> estado = jdbc.queryForMap(
                "SELECT e.nome AS etapa, s.nome AS situacao FROM processo p "
                        + "JOIN etapa e ON e.id = p.etapa_atual_id "
                        + "JOIN situacao s ON s.id = p.situacao_atual_id WHERE p.id = ?",
                processoId);
        assertThat(estado.get("etapa")).isEqualTo("VALIDACAO");
        assertThat(estado.get("situacao")).isEqualTo("EM_VALIDACAO");

        // log da Quarentena com o código e o erro que veio do PostGIS
        List<Map<String, Object>> logs = jdbc.queryForList(
                "SELECT nivel, mensagem, detalhes FROM log_processamento "
                        + "WHERE processo_id = ? AND codigo_erro = 'GEO_ERRO_PROJECAO'",
                processoId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).get("nivel")).isEqualTo("WARNING");
        assertThat((String) logs.get(0).get("mensagem")).contains("Quarentena");
        assertThat((String) logs.get(0).get("detalhes")).contains("transform");

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM auditoria WHERE acao = 'QUARENTENA_GEOESPACIAL' AND detalhes LIKE ?",
                Integer.class, "Processo ID: " + processoId + ";%")).isEqualTo(1);

        // a reprojeção falhou, então não pode ter sobrado cópia de trabalho
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM arquivo_processado WHERE processo_id = ?", Integer.class, processoId))
                .isZero();
    }

    @Test
    void servidorContinuaRespondendoDepoisDaFalhaGeoespacial() throws Exception {
        long processoId = criarCargaComErroDeProjecao();

        mockMvc.perform(post("/processos/{id}/processar", processoId).param("nomeArquivo", "carga.geojson"))
                .andExpect(status().isUnprocessableEntity());

        // mesma carga, logo depois: a API segue de pé e a consulta funciona
        mockMvc.perform(get("/carga/{id}/calculos", processoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("EM_VALIDACAO"));

        mockMvc.perform(get("/processos/metricas"))
                .andExpect(status().isOk());
    }
}
