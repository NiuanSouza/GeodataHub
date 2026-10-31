package com.bifrostconnect.api_geo.integration;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
public class ExportacaoRejeicoesIntegrationTest {

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
    }

    @Test
    @DisplayName("Tarefa 16: Deve exportar as rejeições da quarentena em formato CSV com sucesso")
    @WithMockUser(roles = "AUDITOR")
    void deveExportarRejeicoesCsvComSucesso() throws Exception {
        // 1. Cria um processo base
        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .build();
        p = processoRepository.saveAndFlush(p);

        // 2. Insere um log de erro na quarentena associado ao processo
        jdbcTemplate.update(
            "INSERT INTO log_validacao (processo_id, mensagem, severidade, data_deteccao, geom) VALUES (?, 'Erro de Sobreposição de Polígono', 'CRITICA', CURRENT_TIMESTAMP, ST_GeomFromText('POINT(0 0)', 4326))",
            p.getId()
        );

        // 3. Executa a requisição GET na rota de exportação CSV
        mockMvc.perform(get("/carga/" + p.getId() + "/rejeicoes/exportar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv;charset=UTF-8"))
                // Valida que o Content-Disposition contém o nome gerado pelo Spring
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("rejeicoes_processo_" + p.getId() + ".csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ID_Log;Data_Deteccao;Severidade;Mensagem_Erro")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Erro de Sobreposição de Polígono")));
    }

    @Test
    @DisplayName("Tarefa 16: Deve retornar 404 Not Found se o processo não tiver rejeições")
    @WithMockUser(roles = "AUDITOR")
    void deveRetornar404QuandoNaoHouverRejeicoes() throws Exception {
        mockMvc.perform(get("/carga/99999/rejeicoes/exportar"))
                .andExpect(status().isNotFound());
    }
}