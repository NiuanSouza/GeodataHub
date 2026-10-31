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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.transaction.annotation.Transactional;

// US08 - Tarefa 19: carga em Tratamento e carga em Quarentena não podem aparecer
// no mapa do Analista (GET /mapa/camadas). Roda contra o banco local, com as
// migrations da V22 (versao_fechada / historico_aprovacoes_gestor) aplicadas.
@SpringBootTest
@Transactional
class BlindagemMapaAnalistaIntegrationTest {

    // recorte isolado, longe de qualquer dado real, pra consulta não puxar o banco inteiro
    private static final String BBOX = "-10,-10,-9,-9";
    private static final String POLIGONO = "POLYGON((-9.8 -9.8, -9.8 -9.2, -9.2 -9.2, -9.2 -9.8, -9.8 -9.8))";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("US08 - Tarefa 19: cargas em Tratamento e em Quarentena não vazam pro mapa do Analista")
    @WithMockUser(roles = "ANALISTA")
    void naoDeveRetornarCargasNaoPublicadasParaAnalista() throws Exception {
        Long emTratamento = criarCargaComFeicao("TRATAMENTO", "EM_ANDAMENTO");
        Long emQuarentena = criarCargaComFeicao("VALIDACAO", "EM_VALIDACAO");

        // controle: uma carga publicada no mesmo recorte TEM que aparecer, senão o teste
        // passaria só porque a rota voltou vazia (ou deu 403 pro Analista)
        Long publicada = criarCargaComFeicao("PUBLICACAO", "CONCLUIDA");
        fecharVersao(publicada);

        mockMvc.perform(get("/mapa/camadas").param("bbox", BBOX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[?(@.properties.processoId == " + emTratamento + ")]").isEmpty())
                .andExpect(jsonPath("$.features[?(@.properties.processoId == " + emQuarentena + ")]").isEmpty())
                .andExpect(jsonPath("$.features[?(@.properties.processoId == " + publicada + ")]").isNotEmpty());
    }

    private Long criarCargaComFeicao(String etapa, String situacao) {
        Long processoId = jdbcTemplate.queryForObject("""
            INSERT INTO processo (operador_id, conjunto_id, orgao_id, etapa_atual_id, situacao_atual_id, ano_safra, epsg_origem)
            VALUES (1, 1, 1,
                    (SELECT id FROM etapa WHERE nome = ?),
                    (SELECT id FROM situacao WHERE nome = ?),
                    '2026', '4674')
            RETURNING id
            """, Long.class, etapa, situacao);

        Long arquivoOriginalId = jdbcTemplate.queryForObject("""
            INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256)
            VALUES (?, 1, 'blindagem.geojson', '/tmp/blindagem.geojson', ?)
            RETURNING id
            """, Long.class, processoId, UUID.randomUUID().toString().replace("-", ""));

        Long arquivoProcessadoId = jdbcTemplate.queryForObject("""
            INSERT INTO arquivo_processado (processo_id, arquivo_original_id, etapa_geracao_id, nome_arquivo, url_armazenamento, versao)
            VALUES (?, ?, (SELECT id FROM etapa WHERE nome = 'TRATAMENTO'), 'blindagem_tratado.geojson', '/tmp/blindagem_tratado.geojson', 1)
            RETURNING id
            """, Long.class, processoId, arquivoOriginalId);

        jdbcTemplate.update(
                "INSERT INTO feicao_carga (arquivo_processado_id, codigo_imovel, geom) VALUES (?, ?, ST_GeomFromText(?, 4674))",
                arquivoProcessadoId, "BLINDAGEM_" + processoId, POLIGONO);

        return processoId;
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
