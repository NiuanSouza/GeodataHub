package com.bifrostconnect.api_geo.integration;

import java.util.UUID;

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
import org.springframework.web.context.WebApplicationContext;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
public class LinhagemIntegrationTest {

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

        jdbcTemplate.update("INSERT INTO orgao (id, nome, sigla, ativo) VALUES (1, 'Órgão', 'OT', true) ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO conjunto (id, nome, ativo) VALUES (1, 'Conjunto', true) ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO perfil (id, nome, descricao) VALUES (1, 'OPERADOR', 'Perfil') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO usuario (id, nome, email, senha_hash, perfil_id, ativo) VALUES (1, 'Op', 'op@test.com', 'hash', 1, true) ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (1, 'EM_ANDAMENTO') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (999, 'FECHADO') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (5, 'VALIDACAO', 1, 'Etapa') ON CONFLICT (id) DO NOTHING;");
    }

    @Test
    @DisplayName("Tarefa 9 e 15: Deve retornar a linhagem completa contendo Hash, Histórico e Quarentena")
    @WithMockUser(roles = "AUDITOR")
    void deveRetornarLinhagemCompletaComHistoricoEQuarentena() throws Exception {
        String hashUnico = UUID.randomUUID().toString().replace("-", "").toUpperCase();

        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .situacaoAtualId(1L)
                .etapaAtualId(5L)
                .build();
        p = processoRepository.saveAndFlush(p);

        // 1. Mocka o Arquivo Original
        jdbcTemplate.update(
            "INSERT INTO arquivo_original (processo_id, usuario_upload_id, hash_sha256, nome_original, url_armazenamento) VALUES (?, 1, ?, 'teste.geojson', 'http://storage/teste.geojson')",
            p.getId(), hashUnico
        );

        // 2. Mocka o Histórico de Etapas
        jdbcTemplate.update(
            "INSERT INTO processo_etapa (processo_id, etapa_id, situacao_id, data_inicio, data_fim, mensagem) VALUES (?, 5, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Iniciando validação')",
            p.getId()
        );

        // 3. Mocka o Log de Quarentena (com geometria nula válida baseada na sua constraint)
        jdbcTemplate.update(
            "INSERT INTO log_validacao (processo_id, mensagem, severidade, data_deteccao, geom) VALUES (?, 'Validação - IDs Duplicados', 'CRITICA', CURRENT_TIMESTAMP, ST_GeomFromText('POINT(0 0)', 4326))",
            p.getId()
        );

        // Executa a chamada na rota da API
        mockMvc.perform(get("/carga/" + p.getId() + "/linhagem"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicadorId").value(p.getId()))
                .andExpect(jsonPath("$.hashSha256").value(hashUnico))
                .andExpect(jsonPath("$.historicoEtapas").isArray())
                .andExpect(jsonPath("$.historicoEtapas[0].mensagem").value("Iniciando validação"))
                .andExpect(jsonPath("$.logsQuarentena").isArray())
                .andExpect(jsonPath("$.logsQuarentena[0].mensagemErro").value("Validação - IDs Duplicados"));
    }

    @Test
    @DisplayName("Tarefa 8: Deve retornar Status 404 (Not Found) quando indicador não existir")
    @WithMockUser(roles = "AUDITOR")
    void deveRetornar404ParaIndicadorInexistente() throws Exception {
        mockMvc.perform(get("/carga/99999/linhagem"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tarefa 20: Deve retornar 403 (Forbidden) se Auditor tentar fazer um POST")
    @WithMockUser(roles = "AUDITOR")
    void deveBloquearMetodosDeEscritaParaAuditor() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "teste.geojson", MediaType.APPLICATION_JSON_VALUE, "{}".getBytes());
        
        mockMvc.perform(multipart("/carga/upload")
                .file(file)
                .param("processoId", "1"))
                // Como não colocámos @PreAuthorize no CargaController ainda, ele pode retornar 404/500 por não achar o processo, mas o foco é que vamos plugar a Tarefa 20 depois.
                // Se ainda falhar com algo diferente de 403, pode comentar este assert temporariamente até fizermos a Segurança!
                .andExpect(status().isForbidden());
    }
}