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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
public class MapaIntegrationTest {

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
    @DisplayName("US08 - Tarefa 13: Deve retornar o GeoJSON de revisão com resumo e feições da carga")
    @WithMockUser(roles = "AUDITOR")
    void deveRetornarRevisaoPublicacaoComSucesso() throws Exception {
        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .build();
        p = processoRepository.saveAndFlush(p);

        Long usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuario LIMIT 1", Long.class);
        String hashUnico = UUID.randomUUID().toString().replace("-", "");

        Long arquivoOriginalId = jdbcTemplate.queryForObject(
            "INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256) VALUES (?, ?, 'original.zip', '/path/to/file', ?) RETURNING id",
            Long.class,
            p.getId(),
            usuarioId,
            hashUnico
        );

        Long etapaId = jdbcTemplate.queryForObject("SELECT id FROM etapa LIMIT 1", Long.class);

        Long arquivoProcessadoId = jdbcTemplate.queryForObject(
            "INSERT INTO arquivo_processado (processo_id, arquivo_original_id, etapa_geracao_id, nome_arquivo, url_armazenamento, versao) VALUES (?, ?, ?, 'processado.geojson', '/path/processed', 1) RETURNING id",
            Long.class,
            p.getId(),
            arquivoOriginalId,
            etapaId
        );

        jdbcTemplate.update(
            "INSERT INTO feicao_carga (arquivo_processado_id, codigo_imovel, geom) VALUES (?, 'IMOVEL_001', ST_GeomFromText('POLYGON((0 0, 0 1, 1 1, 1 0, 0 0))', 4674))",
            arquivoProcessadoId
        );

        mockMvc.perform(get("/publicacao/" + p.getId() + "/revisao")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumoAuditoria").exists())
                .andExpect(jsonPath("$.feicoesGeoJson.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.feicoesGeoJson.features").isArray());
    }

    @Test
    @DisplayName("US08 - Tarefa 14: Deve listar camadas em GeoJSON aplicando filtros e Bounding Box")
    @WithMockUser(roles = "OPERADOR")
    void deveListarCamadasMapaGeoJsonComFiltros() throws Exception {
        Processo p = Processo.builder()
                .anoSafra("2026")
                .orgaoId(1L)
                .operadorId(1L)
                .conjuntoId(1L)
                .epsgOrigem("4326")
                .epsgDestino("31983")
                .build();
        p = processoRepository.saveAndFlush(p);

        Long usuarioId = jdbcTemplate.queryForObject("SELECT id FROM usuario LIMIT 1", Long.class);
        Long etapaId = jdbcTemplate.queryForObject("SELECT id FROM etapa LIMIT 1", Long.class);
        String hashUnico2 = UUID.randomUUID().toString().replace("-", "");

        Long arquivoOriginalId = jdbcTemplate.queryForObject(
            "INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256) VALUES (?, ?, 'original2.zip', '/path/to/file2', ?) RETURNING id",
            Long.class,
            p.getId(),
            usuarioId,
            hashUnico2
        );

        Long arquivoProcessadoId = jdbcTemplate.queryForObject(
            "INSERT INTO arquivo_processado (processo_id, arquivo_original_id, etapa_geracao_id, nome_arquivo, url_armazenamento, versao) VALUES (?, ?, ?, 'processado2.geojson', '/path/processed2', 1) RETURNING id",
            Long.class,
            p.getId(),
            arquivoOriginalId,
            etapaId
        );

        jdbcTemplate.update(
            "INSERT INTO feicao_carga (arquivo_processado_id, codigo_imovel, geom) VALUES (?, 'IMOVEL_002', ST_GeomFromText('POLYGON((0 0, 0 2, 2 2, 2 0, 0 0))', 4674))",
            arquivoProcessadoId
        );

        mockMvc.perform(get("/mapa/camadas")
                .param("conjuntoId", "1")
                .param("anoSafra", "2026")
                .param("bbox", "-1,-1,3,3")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isArray());
    }
}