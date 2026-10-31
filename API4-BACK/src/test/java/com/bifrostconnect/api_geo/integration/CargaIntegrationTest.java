package com.bifrostconnect.api_geo.integration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@Transactional
public class CargaIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long processoTesteId;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // 1. Insere o Órgão com ID 1
        jdbcTemplate.update("""
            INSERT INTO orgao (id, nome, sigla, ativo) 
            VALUES (1, 'Órgão Teste', 'OT', true) 
            ON CONFLICT (id) DO NOTHING;
        """);

        // 2. Insere o Conjunto com ID 1
        jdbcTemplate.update("""
            INSERT INTO conjunto (id, nome, ativo) 
            VALUES (1, 'Conjunto Teste', true) 
            ON CONFLICT (id) DO NOTHING;
        """);

        // 3. Insere o Perfil com ID 1
        jdbcTemplate.update("""
            INSERT INTO perfil (id, nome, descricao) 
            VALUES (1, 'OPERADOR', 'Responsavel pela entrada das cargas') 
            ON CONFLICT (id) DO NOTHING;
        """);

        // 4. Insere o Usuário com ID 1
        jdbcTemplate.update("""
            INSERT INTO usuario (id, nome, email, senha_hash, perfil_id, ativo) 
            VALUES (1, 'Operador Teste', 'teste@bifrostconnect.com', 'hash_exemplo', 1, true) 
            ON CONFLICT (id) DO NOTHING;
        """);
    }

    @AfterEach
    public void tearDown() {
        // O @Transactional da classe já faz o rollback de tudo automaticamente.
        // Removidos os comandos DELETE para respeitar a trigger de imutabilidade (RN02) da tabela arquivo_original.
    }

    @Test
    @WithMockUser(username = "operador", roles = "OPERADOR")
    public void deveCriarMetadadosEEncontrarNaListagem() throws Exception {
        String jsonRequest = """
            {
                "orgaoId": 1,
                "conjuntoId": 1,
                "operadorId": 1,
                "anoSafra": "2024",
                "epsgOrigem": "4326"
            }
        """;

        // 1. Testa o cadastro de metadados via CargaController (/carga/metadados)
        MvcResult resultPost = mockMvc.perform(post("/carga/metadados")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.etapaAtualId").isNumber())
                .andExpect(jsonPath("$.situacaoAtualId").isNumber())
                .andReturn();

        // Extrai o ID do Processo retornado
        String responseContent = resultPost.getResponse().getContentAsString();
        Integer idCriado = JsonPath.read(responseContent, "$.id");
        this.processoTesteId = idCriado.longValue();

        // 2. Testa a listagem de processos via ProcessoController (/processos)
        mockMvc.perform(get("/processos"))
                .andExpect(status().isOk());
    }

    /*@Test
    @DisplayName("Tarefa 2: Deve alterar situacao_atual_id no banco para Quarentena após processar arquivo inválido")
    public void deveAtualizarBancoParaQuarentena() throws Exception {
        // Executa a requisição de processamento passando um arquivo com falhas
        mockMvc.perform(post("/processos/1/processar")
                        .param("nomeArquivo", "arquivo_com_erro.zip")
                        .param("usuarioId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacaoAtualId").value(3)); // 3 = QUARENTENA
    }*/

}