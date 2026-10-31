package com.bifrostconnect.api_geo.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

// US08 - Tarefa 12: aprovação do Tratamento gera o pacote GeoJSON pra revisão de Publicação
@SpringBootTest
@Transactional
class ConsolidacaoPublicacaoIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        jdbcTemplate.update("INSERT INTO orgao (id, nome, sigla, ativo) VALUES (1, 'Órgão', 'OT', true) ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO conjunto (id, nome, ativo) VALUES (1, 'Conjunto', true) ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO perfil (id, nome, descricao) VALUES (1, 'OPERADOR', 'Perfil') ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO usuario (id, nome, email, senha_hash, perfil_id, ativo) VALUES (1, 'Op', 'op@test.com', 'hash', 1, true) ON CONFLICT (id) DO NOTHING");
    }

    @Test
    @DisplayName("US08 - Tarefa 12: aprovar o Tratamento consolida geometrias, hectares e linhagem num GeoJSON")
    @WithMockUser(roles = "ADMINISTRADOR")
    void deveConsolidarPacoteAoAprovarTratamento() throws Exception {
        Long processoId = criarCarga("CALCULO_ANALITICO", "CONCLUIDA");
        Long copiaId = criarCopiaTratada(processoId);
        jdbcTemplate.update("""
            INSERT INTO resultados_calculos (processo_id, arquivo_processado_id, codigo_imovel, fonte_camada, codigo_alerta_app, hectares_sobrepostos)
            VALUES (?, ?, 'IMOVEL_T12', 'IBAMA_EMBARGO', 'EMB-1', 12.5)
            """, processoId, copiaId);

        String json = mockMvc.perform(post("/publicacao/{id}/aprovar-tratamento", processoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gestorId\": 1, \"parecer\": \"ok\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_REVISAO"))
                .andExpect(jsonPath("$.quantidadeFeicoes").value(1))
                .andExpect(jsonPath("$.hectaresSobrepostosTotal").value(12.5))
                .andReturn().getResponse().getContentAsString();

        JsonNode resposta = objectMapper.readTree(json);

        // o arquivo gravado é um FeatureCollection com hectares e a linhagem junto
        JsonNode pacote = objectMapper.readTree(Files.readString(Path.of(resposta.get("arquivo").asText())));
        assertThat(pacote.get("type").asText()).isEqualTo("FeatureCollection");
        JsonNode feicao = pacote.get("features").get(0);
        assertThat(feicao.get("geometry").get("type").asText()).isEqualTo("Polygon");
        assertThat(feicao.get("properties").get("areaHectares").asDouble()).isPositive();
        assertThat(feicao.get("properties").get("sobreposicoes")).hasSize(1);
        assertThat(pacote.get("linhagem").get("hashSha256").asText()).isNotBlank();
        assertThat(pacote.get("metadados").get("arquivoProcessadoOrigemId").asLong()).isEqualTo(copiaId);

        // aprovação ficou no histórico e a carga andou pra Publicação
        Integer aprovacoes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM historico_aprovacoes_gestor WHERE processo_id = ? AND etapa_avaliada = 'TRATAMENTO' AND decisao = 'APROVADO'",
                Integer.class, processoId);
        assertThat(aprovacoes).isEqualTo(1);

        String etapaAtual = jdbcTemplate.queryForObject(
                "SELECT e.nome FROM processo p JOIN etapa e ON e.id = p.etapa_atual_id WHERE p.id = ?",
                String.class, processoId);
        assertThat(etapaAtual).isEqualTo("PUBLICACAO");

        // segunda aprovação com pacote pendente dá conflito
        mockMvc.perform(post("/publicacao/{id}/aprovar-tratamento", processoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gestorId\": 1}"))
                .andExpect(status().isConflict());

        Files.deleteIfExists(Path.of(resposta.get("arquivo").asText()));
    }

    @Test
    @DisplayName("US08 - Tarefa 12: carga em quarentena não pode ser consolidada")
    @WithMockUser(roles = "ADMINISTRADOR")
    void naoDeveConsolidarCargaEmQuarentena() throws Exception {
        Long processoId = criarCarga("VALIDACAO", "EM_VALIDACAO");
        criarCopiaTratada(processoId);

        mockMvc.perform(post("/publicacao/{id}/aprovar-tratamento", processoId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gestorId\": 1}"))
                .andExpect(status().isConflict());

        Integer pacotes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM publicacao WHERE processo_id = ?", Integer.class, processoId);
        assertThat(pacotes).isZero();
    }

    private Long criarCarga(String etapa, String situacao) {
        Long processoId = jdbcTemplate.queryForObject("""
            INSERT INTO processo (operador_id, conjunto_id, orgao_id, etapa_atual_id, situacao_atual_id, ano_safra, epsg_origem)
            VALUES (1, 1, 1, (SELECT id FROM etapa WHERE nome = ?), (SELECT id FROM situacao WHERE nome = ?), '2026', '4674')
            RETURNING id
            """, Long.class, etapa, situacao);

        jdbcTemplate.update("""
            INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256)
            VALUES (?, 1, 't12.geojson', '/tmp/t12.geojson', ?)
            """, processoId, UUID.randomUUID().toString().replace("-", ""));
        return processoId;
    }

    private Long criarCopiaTratada(Long processoId) {
        Long copiaId = jdbcTemplate.queryForObject("""
            INSERT INTO arquivo_processado (processo_id, arquivo_original_id, etapa_geracao_id, nome_arquivo, url_armazenamento, versao)
            VALUES (?, (SELECT id FROM arquivo_original WHERE processo_id = ?), (SELECT id FROM etapa WHERE nome = 'TRATAMENTO'),
                    't12_tratado.geojson', '/tmp/t12_tratado.geojson', 1)
            RETURNING id
            """, Long.class, processoId, processoId);

        jdbcTemplate.update("""
            INSERT INTO feicao_carga (arquivo_processado_id, codigo_imovel, geom)
            VALUES (?, 'IMOVEL_T12', ST_GeomFromText('POLYGON((-54.6 -20.6, -54.6 -20.5, -54.5 -20.5, -54.5 -20.6, -54.6 -20.6))', 4674))
            """, copiaId);
        return copiaId;
    }
}
