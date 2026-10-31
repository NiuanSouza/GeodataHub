package com.bifrostconnect.api_geo.integration;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
public class CancelamentoCargaIntegrationTest {

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
    @DisplayName("Tarefa 19: Deve cancelar/invalidar carga com sucesso informando o motivo (204 No Content)")
    @WithMockUser(roles = "ADMINISTRADOR")
    void deveCancelarCargaComSucesso() throws Exception {
        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .build();
        p = processoRepository.saveAndFlush(p);

        String jsonBody = "{\"motivo\": \"Dados geográficos inconsistentes com a base oficial.\"}";

        mockMvc.perform(patch("/carga/" + p.getId() + "/cancelar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Tarefa 19: Deve retornar 404 Not Found se o processo a ser cancelado não existir")
    @WithMockUser(roles = "ADMINISTRADOR")
    void deveRetornar404QuandoProcessoNaoExistir() throws Exception {
        String jsonBody = "{\"motivo\": \"Processo inexistente.\"}";

        mockMvc.perform(patch("/carga/99999/cancelar")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonBody))
                .andExpect(status().isNotFound());
    }
}