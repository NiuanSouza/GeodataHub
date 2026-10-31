package com.bifrostconnect.api_geo.integration;

import com.bifrostconnect.api_geo.controller.ProcessoController;
import com.bifrostconnect.api_geo.repository.ArquivoOriginalRepository;
import com.bifrostconnect.api_geo.service.ProcessoDashboardService;
import com.bifrostconnect.api_geo.service.ProcessoService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VersaoFechadaIntegrationTest {

    private static final String SQLSTATE_RAISE = "P0001"; // Default PostgreSQL RAISE EXCEPTION state

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProcessoService processoService;

    @MockBean
    private ProcessoDashboardService processoDashboardService;

    @MockBean
    private ArquivoOriginalRepository arquivoOriginalRepository;

    private record Cenario(
            long gestorId,
            long processoId,
            long arquivoId,
            long resultadoId) {
    }

    // ---------- helpers ----------

    private Cenario criarCenario() {
        String s = UUID.randomUUID().toString().substring(0, 8);

        Long gestor = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash, perfil_id, ativo) "
                        + "VALUES (?, ?, 'hash', "
                        + "(SELECT id FROM perfil WHERE nome = 'ADMINISTRADOR'), TRUE) "
                        + "RETURNING id",
                Long.class,
                "Gestor " + s,
                "gestor." + s + "@teste.local");

        Long processo = jdbc.queryForObject(
                "INSERT INTO processo "
                        + "(operador_id, conjunto_id, orgao_id, ano_safra, epsg_origem) "
                        + "VALUES (?, (SELECT min(id) FROM conjunto), "
                        + "(SELECT min(id) FROM orgao), '2025/2026', '4674') "
                        + "RETURNING id",
                Long.class,
                gestor);

        String hash = (UUID.randomUUID().toString()
                + UUID.randomUUID()).replace("-", "");

        Long original = jdbc.queryForObject(
                "INSERT INTO arquivo_original "
                        + "(processo_id, usuario_upload_id, nome_original, "
                        + "url_armazenamento, hash_sha256) "
                        + "VALUES (?, ?, 'teste.zip', 'teste/teste.zip', ?) "
                        + "RETURNING id",
                Long.class,
                processo,
                gestor,
                hash);

        Long arquivo = jdbc.queryForObject(
                "INSERT INTO arquivo_processado "
                        + "(arquivo_original_id, processo_id, etapa_geracao_id, "
                        + "nome_arquivo, url_armazenamento, versao) "
                        + "VALUES (?, ?, "
                        + "(SELECT id FROM etapa WHERE nome = 'TRATAMENTO'), "
                        + "'tratado.gpkg', 'teste/tratado.gpkg', 1) "
                        + "RETURNING id",
                Long.class,
                original,
                processo);

        Long resultado = jdbc.queryForObject(
                "INSERT INTO resultados_calculos "
                        + "(processo_id, arquivo_processado_id, codigo_imovel, "
                        + "fonte_camada, codigo_alerta_app, hectares_sobrepostos) "
                        + "VALUES (?, ?, 'CAR-TESTE', 'FUNAI_TERRA_INDIGENA', "
                        + "'ALERTA-TESTE', 123.4567) "
                        + "RETURNING id",
                Long.class,
                processo,
                arquivo);

        return new Cenario(gestor, processo, arquivo, resultado);
    }

    private void aprovarPublicacao(Cenario c) {
        jdbc.update(
                "INSERT INTO historico_aprovacoes_gestor "
                        + "(processo_id, gestor_id, etapa_avaliada, decisao) "
                        + "VALUES (?, ?, 'PUBLICACAO', 'APROVADO')",
                c.processoId(),
                c.gestorId());
    }

    private void fecharVersao(Cenario c) {
        jdbc.update(
                "UPDATE processo SET versao_oficial = '1.0', "
                        + "versao_fechada = TRUE, data_publicacao = now(), "
                        + "gestor_publicacao_id = ? WHERE id = ?",
                c.gestorId(),
                c.processoId());
    }

    private Cenario cenarioComVersaoFechada() {
        Cenario c = criarCenario();
        aprovarPublicacao(c);
        fecharVersao(c);
        return c;
    }

    /** Passa somente se o comando for barrado por uma trigger (RAISE EXCEPTION). */
    private void esperaBloqueioPorTrigger(Runnable comando) {
        try {
            comando.run();
        } catch (DataAccessException e) {
            Throwable raiz = NestedExceptionUtils.getMostSpecificCause(e);

            assertThat(raiz).isInstanceOf(SQLException.class);

            assertThat(((SQLException) raiz).getSQLState())
                    .as("o erro deveria vir de RAISE EXCEPTION, mas veio: %s",
                            raiz.getMessage())
                    .isEqualTo(SQLSTATE_RAISE);

            return;
        }

        fail("Esperava bloqueio pela trigger, mas o comando foi executado");
    }

    @Test
    void versaoAbertaAindaAceitaUpdate() {
        Cenario c = criarCenario();

        int linhas = jdbc.update(
                "UPDATE resultados_calculos "
                        + "SET hectares_sobrepostos = 200 WHERE id = ?",
                c.resultadoId());

        assertThat(linhas).isEqualTo(1);
    }

    @Test
    void updateDeHectaresEmVersaoFechadaDeveSerBloqueado() {
        Cenario c = cenarioComVersaoFechada();

        esperaBloqueioPorTrigger(() ->
                jdbc.update(
                        "UPDATE resultados_calculos "
                                + "SET hectares_sobrepostos = 0 WHERE id = ?",
                        c.resultadoId()));
    }

    @Test
    void deleteDeResultadoEmVersaoFechadaDeveSerBloqueado() {
        Cenario c = cenarioComVersaoFechada();

        esperaBloqueioPorTrigger(() ->
                jdbc.update(
                        "DELETE FROM resultados_calculos WHERE id = ?",
                        c.resultadoId()));
    }

    @Test
    void updateEmArquivoProcessadoDeVersaoFechadaDeveSerBloqueado() {
        Cenario c = cenarioComVersaoFechada();

        esperaBloqueioPorTrigger(() ->
                jdbc.update(
                        "UPDATE arquivo_processado "
                                + "SET nome_arquivo = 'alterado' WHERE id = ?",
                        c.arquivoId()));
    }

    @Test
    void reabrirVersaoFechadaDeveSerBloqueado() {
        Cenario c = cenarioComVersaoFechada();

        esperaBloqueioPorTrigger(() ->
                jdbc.update(
                        "UPDATE processo SET versao_fechada = FALSE WHERE id = ?",
                        c.processoId()));
    }

    @Test
    void fecharVersaoSemAprovacaoNaPublicacaoDeveSerBloqueado() {
        Cenario c = criarCenario();

        esperaBloqueioPorTrigger(() -> fecharVersao(c));
    }

    @Test
    void aprovadorInativoDeveSerRecusado() {
        Cenario c = criarCenario();

        Long inativo = jdbc.queryForObject(
                "INSERT INTO usuario "
                        + "(nome, email, senha_hash, perfil_id, ativo) "
                        + "VALUES ('Inativo', ?, 'hash', "
                        + "(SELECT id FROM perfil WHERE nome = 'ADMINISTRADOR'), FALSE) "
                        + "RETURNING id",
                Long.class,
                "inativo." + UUID.randomUUID() + "@teste.local");

        esperaBloqueioPorTrigger(() ->
                jdbc.update(
                        "INSERT INTO historico_aprovacoes_gestor "
                                + "(processo_id, gestor_id, etapa_avaliada, decisao) "
                                + "VALUES (?, ?, 'PUBLICACAO', 'APROVADO')",
                        c.processoId(),
                        inativo));
    } // <--- Chave de fecho do método que estava a faltar!

    @Test
    @DisplayName("Tarefa 9: Deve retornar 409 ao tentar atualizar um processo fechado")
    @WithMockUser(roles = "GESTOR")
    void deveRecusarAtualizacaoEmVersaoFechada() throws Exception {
        Long idProcessoFechado = 999L;

        doThrow(new IllegalStateException(
                "Não é permitido alterar um processo fechado."
        ))
                .when(processoService)
                .alocarEditorEAtualizarSituacao(eq(idProcessoFechado), anyLong());

        mockMvc.perform(put("/processos/{id}/editor", idProcessoFechado)
                        .with(csrf()) 
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"editorId\": 1}"))
                .andExpect(status().isConflict());
    }
}