package com.bifrostconnect.api_geo.integration;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class ReprocessamentoCargaIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        // Insere as etapas exigidas pelo fluxo
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (1, 'CARGA_E_VALIDACAO', 1, 'Carga') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (2, 'TRATAMENTO', 2, 'Tratamento') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (3, 'VALIDACAO', 3, 'Validacao') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (4, 'CALCULO_ANALITICO', 4, 'Calculo') ON CONFLICT (id) DO NOTHING;");

        // Insere as situações exigidas
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (1, 'EM_ANDAMENTO') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (2, 'CONCLUIDA') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (3, 'FALHOU') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (4, 'QUARENTENA') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (999, 'FECHADO') ON CONFLICT (id) DO NOTHING;");

        // Insere as entidades relacionadas para satisfazer as chaves estrangeiras
        jdbcTemplate.update("""
            INSERT INTO orgao (id, nome, sigla, ativo) 
            VALUES (1, 'Órgão Teste', 'OT', true) 
            ON CONFLICT (id) DO NOTHING;
        """);

        jdbcTemplate.update("""
            INSERT INTO conjunto (id, nome, ativo) 
            VALUES (1, 'Conjunto Teste', true) 
            ON CONFLICT (id) DO NOTHING;
        """);

        jdbcTemplate.update("""
            INSERT INTO perfil (id, nome, descricao) 
            VALUES (1, 'OPERADOR', 'Responsavel pela entrada das cargas') 
            ON CONFLICT (id) DO NOTHING;
        """);

        jdbcTemplate.update("""
            INSERT INTO usuario (id, nome, email, senha_hash, perfil_id, ativo) 
            VALUES (1, 'Operador Teste', 'teste@bifrostconnect.com', 'hash_exemplo', 1, true) 
            ON CONFLICT (id) DO NOTHING;
        """);
    }

    @Test
    @DisplayName("Tarefa 18: Operador deve conseguir enviar novo arquivo corrigido e reiniciar reprocessamento com sucesso")
    @WithMockUser(roles = "OPERADOR")
    void deveReprocessarCargaComSucesso() throws Exception {
        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .etapaAtualId(1L)
                .situacaoAtualId(1L)
                .build();
        p = processoRepository.saveAndFlush(p);

        // Inserção explícita na tabela processo_etapa para satisfazer a FK exigida pelo banco
        jdbcTemplate.update(
            "INSERT INTO processo_etapa (processo_id, etapa_id, situacao_id, data_inicio) VALUES (?, 1, 1, NOW())",
            p.getId()
        );

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "arquivo_corrigido.geojson",
                MediaType.APPLICATION_JSON_VALUE,
                ("{\"type\": \"FeatureCollection\", \"features\": [], \"nonce\": " + System.currentTimeMillis() + "}").getBytes()
        );

        mockMvc.perform(multipart("/carga/" + p.getId() + "/reprocessar")
                .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nomeOriginal").value("arquivo_corrigido.geojson"))
                .andExpect(jsonPath("$.hashSha256").isNotEmpty());
    }
}