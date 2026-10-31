package com.bifrostconnect.api_geo.integration;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class EncaminhamentoQuarentenaIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ProcessoRepository processoRepository;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        this.mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("Tarefa 17: Auditor deve conseguir notificar operador e liberar chamado com sucesso (204 No Content)")
    @WithMockUser(roles = "AUDITOR")
    void deveNotificarOperadorComSucesso() throws Exception {
        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .build();
        p = processoRepository.saveAndFlush(p);

        mockMvc.perform(post("/carga/" + p.getId() + "/quarentena/notificar-operador"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Tarefa 17: Deve retornar 404 Not Found se o processo não existir")
    @WithMockUser(roles = "AUDITOR")
    void deveRetornar404QuandoProcessoNaoExistir() throws Exception {
        mockMvc.perform(post("/carga/99999/quarentena/notificar-operador"))
                .andExpect(status().isNotFound());
    }
}