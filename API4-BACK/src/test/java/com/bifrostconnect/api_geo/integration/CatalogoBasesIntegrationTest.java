package com.bifrostconnect.api_geo.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

// US09 - Tarefas 7 e 8: catálogo de bases do Analista (GET /catalogo/bases).
// Roda contra o banco local, com a V22 (versao_fechada / historico_aprovacoes_gestor) aplicada.
@SpringBootTest
@Transactional
class CatalogoBasesIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    // cada teste usa um ano_safra próprio, assim o filtro isola só as bases criadas aqui
    // e o resto do banco local não interfere
    private String anoSafra;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        anoSafra = "T" + ThreadLocalRandom.current().nextInt(100_000_000, 999_999_999);

        jdbcTemplate.update("INSERT INTO orgao (id, nome, sigla, ativo) VALUES (1, 'Órgão', 'OT', true) ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO conjunto (id, nome, ativo) VALUES (1, 'Conjunto', true) ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO perfil (id, nome, descricao) VALUES (1, 'OPERADOR', 'Perfil') ON CONFLICT (id) DO NOTHING");
        jdbcTemplate.update("INSERT INTO usuario (id, nome, email, senha_hash, perfil_id, ativo) VALUES (1, 'Op', 'op@test.com', 'hash', 1, true) ON CONFLICT (id) DO NOTHING");
    }

    @Test
    @DisplayName("US09 - Tarefa 7: catálogo só traz base em Publicação + Concluída (com versão fechada)")
    @WithMockUser(roles = "ANALISTA")
    void deveListarSomenteBasesPublicadas() throws Exception {
        Long emTratamento = criarBase("TRATAMENTO", "EM_ANDAMENTO");
        Long emQuarentena = criarBase("VALIDACAO", "EM_VALIDACAO");
        Long publicacaoEmAndamento = criarBase("PUBLICACAO", "EM_ANDAMENTO");
        Long publicada = criarBase("PUBLICACAO", "CONCLUIDA");
        fecharVersao(publicada);

        mockMvc.perform(get("/catalogo/bases").param("anoSafra", anoSafra))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(publicada))
                .andExpect(jsonPath("$[?(@.id == " + emTratamento + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + emQuarentena + ")]").isEmpty())
                .andExpect(jsonPath("$[?(@.id == " + publicacaoEmAndamento + ")]").isEmpty());
    }

    @Test
    @DisplayName("US09 - Tarefa 8: catálogo vazio devolve 204 sem corpo")
    @WithMockUser(roles = "ANALISTA")
    void deveRetornar204QuandoCatalogoVazio() throws Exception {
        // tem base nesse ano, mas nenhuma publicada: pela trava o catálogo fica vazio
        criarBase("TRATAMENTO", "EM_ANDAMENTO");

        mockMvc.perform(get("/catalogo/bases").param("anoSafra", anoSafra))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    @DisplayName("US09 - Tarefa 8: detalhe de base publicada devolve 200")
    @WithMockUser(roles = "ANALISTA")
    void deveRetornarDetalhesDaBasePublicada() throws Exception {
        Long publicada = criarBase("PUBLICACAO", "CONCLUIDA");
        fecharVersao(publicada);

        mockMvc.perform(get("/catalogo/bases/{id}", publicada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(publicada))
                .andExpect(jsonPath("$.ano_safra").value(anoSafra))
                .andExpect(jsonPath("$.versao_oficial").value("v1"));
    }

    @Test
    @DisplayName("US09 - Tarefa 8: ID de base inexistente devolve 404 padronizado")
    @WithMockUser(roles = "ANALISTA")
    void deveRetornar404ParaBaseInexistente() throws Exception {
        mockMvc.perform(get("/catalogo/bases/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("US09 - Tarefas 7 e 8: base que existe mas não foi publicada também é 404 pro Analista")
    @WithMockUser(roles = "ANALISTA")
    void deveRetornar404ParaBaseNaoPublicada() throws Exception {
        Long emTratamento = criarBase("TRATAMENTO", "EM_ANDAMENTO");

        mockMvc.perform(get("/catalogo/bases/{id}", emTratamento))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists());
    }

    private Long criarBase(String etapa, String situacao) {
        return jdbcTemplate.queryForObject("""
            INSERT INTO processo (operador_id, conjunto_id, orgao_id, etapa_atual_id, situacao_atual_id, ano_safra, epsg_origem)
            VALUES (1, 1, 1,
                    (SELECT id FROM etapa WHERE nome = ?),
                    (SELECT id FROM situacao WHERE nome = ?),
                    ?, '4674')
            RETURNING id
            """, Long.class, etapa, situacao, anoSafra);
    }

    // a V22 só deixa fechar a versão com aprovação do gestor na PUBLICACAO e os dados oficiais preenchidos
    private void fecharVersao(Long processoId) {
        jdbcTemplate.update("""
            INSERT INTO historico_aprovacoes_gestor (processo_id, gestor_id, etapa_avaliada, decisao)
            VALUES (?, 1, 'PUBLICACAO', 'APROVADO')
            """, processoId);
        jdbcTemplate.update("""
            UPDATE processo
               SET versao_oficial = 'v1', data_publicacao = now(), gestor_publicacao_id = 1, versao_fechada = TRUE
             WHERE id = ?
            """, processoId);
    }
}
