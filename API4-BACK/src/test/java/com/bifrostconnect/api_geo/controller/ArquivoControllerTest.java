package com.bifrostconnect.api_geo.controller;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import com.bifrostconnect.api_geo.service.ProcessoService;
import com.bifrostconnect.api_geo.service.ValidacaoService;

@SpringBootTest
class ArquivoControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ValidacaoService validacaoService;

    @Autowired
    private ProcessoService processoService;

    private Long processoId;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        // Limpeza com TRUNCATE CASCADE para ignorar as triggers de bloqueio de DELETE (imutabilidade)
        jdbcTemplate.execute("TRUNCATE TABLE processo, arquivo_original, arquivo_processado, feicao_carga, resultados_calculos, historico_aprovacoes_gestor, log_processamento, log_validacao, auditoria, processo_etapa CASCADE");

        // Insere todas as etapas exigidas pelo fluxo de processamento
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (1, 'CARGA_E_VALIDACAO', 1, 'Carga') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (2, 'TRATAMENTO', 2, 'Tratamento') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (3, 'VALIDACAO', 3, 'Validacao') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO etapa (id, nome, ordem, descricao) VALUES (4, 'CALCULO_ANALITICO', 4, 'Calculo') ON CONFLICT (id) DO NOTHING;");

        // Insere todas as situações exigidas
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (1, 'EM_ANDAMENTO') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (2, 'CONCLUIDA') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (3, 'FALHOU') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (4, 'QUARENTENA') ON CONFLICT (id) DO NOTHING;");
        jdbcTemplate.update("INSERT INTO situacao (id, nome) VALUES (999, 'FECHADO') ON CONFLICT (id) DO NOTHING;");

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

        // Este insert será persistido imediatamente e ficará visível para as transações internas (REQUIRES_NEW)
        this.processoId = jdbcTemplate.queryForObject(
            """
            INSERT INTO processo (ano_safra, epsg_origem, epsg_destino, orgao_id, operador_id, conjunto_id, situacao_atual_id, etapa_atual_id, data_criacao)
            VALUES ('2025', '4326', '31983', 1, 1, 1, 1, 1, NOW())
            RETURNING id;
            """,
            Long.class
        );
    }

    @Test
    @DisplayName("Deve realizar o upload de arquivo na Zona Bruta com sucesso")
    @WithMockUser(username = "operador", roles = "OPERADOR")
    void deveFazerUploadDeArquivo() throws Exception {
        String conteudoUnico = "{\"type\": \"FeatureCollection\", \"features\": [], \"nonce\": " + System.currentTimeMillis() + "}";
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malha_sidrolandia.geojson",
                "application/json",
                conteudoUnico.getBytes()
        );

        mockMvc.perform(multipart("/arquivos/upload")
                        .file(file)
                        .param("processoId", processoId.toString())
                        .param("usuarioId", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sucesso").value(true))
                .andExpect(jsonPath("$.mensagem").value("Carga realizada na Zona Bruta com sucesso!"))
                .andExpect(jsonPath("$.hash_sha256").exists())
                .andExpect(jsonPath("$.id_arquivo").exists());
    }

    @Test
    @DisplayName("Tarefa 1 (Motor Espacial): Deve ler GeoJSON e validar motor espacial sem sobreposição")
    @WithMockUser(username = "operador", roles = "OPERADOR")
    void deveValidarMotorEspacialComSucesso() throws Exception {
        String geoJsonConteudo = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "nome": "Polígono Teste" },
                  "geometry": {
                    "type": "Polygon",
                    "coordinates": [
                      [
                        [-46.633308, -23.550520],
                        [-46.633308, -23.551520],
                        [-46.632308, -23.551520],
                        [-46.632308, -23.550520],
                        [-46.633308, -23.550520]
                      ]
                    ]
                  }
                }
              ]
            }
        """;

        Path arquivoTemp = Files.createTempFile("teste_motor_espacial", ".geojson");
        Files.writeString(arquivoTemp, geoJsonConteudo);

        Processo processo = processoRepository.findById(processoId).orElseThrow();

        boolean valido = validacaoService.executarValidacoes(processo, "teste_motor_espacial.geojson", arquivoTemp);

        assertTrue(valido, "A validação do motor espacial deve passar sem sobreposições prévias.");

        Files.deleteIfExists(arquivoTemp);  
    }

    @Test
    @DisplayName("Tarefa 2 (Gatilho Quarentena): Arquivo ilegível deve falhar o TRATAMENTO (situacaoAtualId = 4 / FALHOU) e responder 422")
    void deveFalharTratamentoQuandoArquivoForIlegivel() throws Exception {
        Long situacaoFalhouEsperada = 4L;

        Path arquivoInvalido = Files.createTempFile(
                "arquivo_invalido", ".geojson");

        try {
            Files.writeString(
                    arquivoInvalido,
                    "Conteudo invalido para geojson");

            Processo processo = processoRepository.findById(processoId)
                    .orElseThrow();
            assertThrows(ResponseStatusException.class, () ->
                    processoService.processarArquivo(
                            processo,
                            arquivoInvalido.getFileName().toString(),
                            arquivoInvalido,
                            1L));

            Long situacaoGravada = jdbcTemplate.queryForObject(
                    "SELECT situacao_atual_id FROM processo WHERE id = ?",
                    Long.class, processoId);
            assertEquals(situacaoFalhouEsperada, situacaoGravada);

        } finally {
            Files.deleteIfExists(arquivoInvalido);
        }
    }

    @Test
    @DisplayName("Tarefa 9: EPSG detectado diferente do declarado => COM_RESSALVA, EPSG detectado gravado à parte e processamento interrompido")
    void deveFicarComRessalvaQuandoEpsgDetectadoDivergirDoDeclarado() throws Exception {
        Long situacaoComRessalvaEsperada = 3L; 

        Path arquivo = Files.createTempFile("epsg_divergente", ".geojson");

        try {
            Files.writeString(arquivo, """
                {"type":"FeatureCollection",
                 "crs":{"type":"name","properties":{"name":"urn:ogc:def:crs:EPSG::4674"}},
                 "features":[]}
                """);

            Processo processo = processoRepository.findById(processoId).orElseThrow();

            Processo resultado = processoService.processarArquivo(
                    processo, arquivo.getFileName().toString(), arquivo, 1L);

            assertEquals(situacaoComRessalvaEsperada, resultado.getSituacaoAtualId());

            assertEquals("4326", jdbcTemplate.queryForObject(
                    "SELECT epsg_origem FROM processo WHERE id = ?", String.class, processoId));
            assertEquals("4674", jdbcTemplate.queryForObject(
                    "SELECT epsg_detectado FROM processo WHERE id = ?", String.class, processoId));

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM arquivo_processado WHERE processo_id = ?", Integer.class, processoId));

            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM log_processamento WHERE processo_id = ? AND nivel = 'WARNING'",
                    Integer.class, processoId) > 0);

        } finally {
            Files.deleteIfExists(arquivo);
        }
    }

    @Test
    @DisplayName("Tarefa 9: EPSG confere => cópia de trabalho convertida para EPSG:4674 e registrada em arquivo_processado")
    void deveConverterCopiaDeTrabalhoParaEpsg4674QuandoEpsgConferir() throws Exception {
        Path arquivo = Files.createTempFile("epsg_confere", ".geojson");
        Path copia = null;

        try {
            Files.writeString(arquivo, """
                {"type":"FeatureCollection","features":[{"type":"Feature","properties":{"nome":"Teste"},
                 "geometry":{"type":"Polygon","coordinates":[[[-46.633308,-23.550520],[-46.633308,-23.551520],
                 [-46.632308,-23.551520],[-46.632308,-23.550520],[-46.633308,-23.550520]]]}}]}
                """);

            String hashOriginal = (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID())
                    .replace("-", "").substring(0, 64);
            jdbcTemplate.update("""
                INSERT INTO arquivo_original
                    (processo_id, usuario_upload_id, nome_original, extensao, tipo_mime,
                     tamanho_bytes, url_armazenamento, hash_sha256)
                VALUES (?, 1, 'epsg_confere.geojson', 'geojson', 'application/json', ?, ?, ?)
                """, processoId, Files.size(arquivo), arquivo.toString(), hashOriginal);

            Processo processo = processoRepository.findById(processoId).orElseThrow();
            processoService.processarArquivo(
                    processo, "epsg_confere.geojson", arquivo, 1L);

            assertEquals("4326", jdbcTemplate.queryForObject(
                    "SELECT epsg_detectado FROM processo WHERE id = ?", String.class, processoId));
            assertEquals("4674", jdbcTemplate.queryForObject(
                    "SELECT epsg_destino FROM processo WHERE id = ?", String.class, processoId));

            var registro = jdbcTemplate.queryForMap(
                    "SELECT epsg, versao, url_armazenamento, hash_sha256 FROM arquivo_processado WHERE processo_id = ?",
                    processoId);
            assertEquals("4674", registro.get("epsg"));
            assertEquals(1, registro.get("versao"));

            copia = Path.of((String) registro.get("url_armazenamento"));
            assertTrue(Files.exists(copia), "A cópia de trabalho deve existir em disco.");
            assertTrue(Files.readString(copia).contains("urn:ogc:def:crs:EPSG::4674"),
                    "A cópia deve declarar o CRS oficial.");

            assertTrue(Files.readString(arquivo).contains("-46.633308"));

        } finally {
            Files.deleteIfExists(arquivo);
            if (copia != null) {
                Files.deleteIfExists(copia);
            }
        }
    }

    @Test
    @DisplayName("Tarefa 12: polígonos sobrepostos => Validação interrompida e carga na Quarentena (VALIDACAO / EM_VALIDACAO)")
    void deveMandarParaQuarentenaQuandoPoligonosSeSobrepoem() throws Exception {
        Long etapaValidacao = 3L;
        Long situacaoEmValidacao = 2L;

        Path arquivo = Files.createTempFile("sobreposicao", ".geojson");
        Path copia = null;

        try {
            Files.writeString(arquivo, """
                {"type":"FeatureCollection","features":[
                 {"type":"Feature","properties":{"cod_imovel":"CAR-A"},
                  "geometry":{"type":"Polygon","coordinates":[[[-46.634,-23.551],[-46.634,-23.552],
                  [-46.632,-23.552],[-46.632,-23.551],[-46.634,-23.551]]]}},
                 {"type":"Feature","properties":{"cod_imovel":"CAR-B"},
                  "geometry":{"type":"Polygon","coordinates":[[[-46.633,-23.551],[-46.633,-23.552],
                  [-46.631,-23.552],[-46.631,-23.551],[-46.633,-23.551]]]}}]}
                """);

            String hashOriginal = (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID())
                    .replace("-", "").substring(0, 64);
            jdbcTemplate.update("""
                INSERT INTO arquivo_original
                    (processo_id, usuario_upload_id, nome_original, extensao, tipo_mime,
                     tamanho_bytes, url_armazenamento, hash_sha256)
                VALUES (?, 1, 'sobreposicao.geojson', 'geojson', 'application/json', ?, ?, ?)
                """, processoId, Files.size(arquivo), arquivo.toString(), hashOriginal);

            Processo processo = processoRepository.findById(processoId).orElseThrow();
            Processo resultado = processoService.processarArquivo(
                    processo, "sobreposicao.geojson", arquivo, 1L);

            assertEquals(etapaValidacao, resultado.getEtapaAtualId());
            assertEquals(situacaoEmValidacao, resultado.getSituacaoAtualId());
            assertEquals(situacaoEmValidacao, jdbcTemplate.queryForObject(
                    "SELECT situacao_atual_id FROM processo WHERE id = ?", Long.class, processoId));

            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM resultados_calculos WHERE processo_id = ?", Integer.class, processoId));
            assertEquals(0, jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM feicao_carga fc
                      JOIN arquivo_processado ap ON ap.id = fc.arquivo_processado_id
                     WHERE ap.processo_id = ? AND fc.atributos->>'area_hectares' IS NOT NULL
                    """, Integer.class, processoId));

            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM log_processamento WHERE processo_id = ? AND nivel = 'WARNING' AND mensagem LIKE '%Quarentena%'",
                    Integer.class, processoId) > 0);

            copia = Path.of(jdbcTemplate.queryForObject(
                    "SELECT url_armazenamento FROM arquivo_processado WHERE processo_id = ?", String.class, processoId));

        } finally {
            Files.deleteIfExists(arquivo);
            if (copia != null) {
                Files.deleteIfExists(copia);
            }
        }
    }

    @Test
    @DisplayName("Tarefa 13: GET /carga/{id}/calculos devolve área total, área por polígono, interseções e alertas do Tratamento")
    void deveConsultarCalculosDaCarga() throws Exception {
        Path arquivo = Files.createTempFile("calculos_carga", ".geojson");
        Path copia = null;

        jdbcTemplate.update("""
            INSERT INTO camada_referencia (fonte, nome_camada, identificador_origem, geom)
            VALUES ('IBAMA_EMBARGO', 'Embargo teste T13', 'EMB-T13',
                    ST_GeomFromText('POLYGON((-46.6335 -23.551, -46.6335 -23.552, -46.6325 -23.552,
                                              -46.6325 -23.551, -46.6335 -23.551))', 4674))
            """);

        try {
            Files.writeString(arquivo, """
                {"type":"FeatureCollection","features":[
                 {"type":"Feature","properties":{"cod_imovel":"CAR-A"},
                  "geometry":{"type":"Polygon","coordinates":[[[-46.634,-23.551],[-46.634,-23.552],
                  [-46.633,-23.552],[-46.633,-23.551],[-46.634,-23.551]]]}},
                 {"type":"Feature","properties":{"cod_imovel":"CAR-B"},
                  "geometry":{"type":"Polygon","coordinates":[[[-46.632,-23.551],[-46.632,-23.552],
                  [-46.631,-23.552],[-46.631,-23.551],[-46.632,-23.551]]]}}]}
                """);

            String hashOriginal = (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID())
                    .replace("-", "").substring(0, 64);
            jdbcTemplate.update("""
                INSERT INTO arquivo_original
                    (processo_id, usuario_upload_id, nome_original, extensao, tipo_mime,
                     tamanho_bytes, url_armazenamento, hash_sha256)
                VALUES (?, 1, 'calculos_carga.geojson', 'geojson', 'application/json', ?, ?, ?)
                """, processoId, Files.size(arquivo), arquivo.toString(), hashOriginal);

            Processo processo = processoRepository.findById(processoId).orElseThrow();
            processoService.processarArquivo(processo, "calculos_carga.geojson", arquivo, 1L);

            copia = Path.of(jdbcTemplate.queryForObject(
                    "SELECT url_armazenamento FROM arquivo_processado WHERE processo_id = ?", String.class, processoId));
            Long arquivoProcessadoId = jdbcTemplate.queryForObject(
                    "SELECT id FROM arquivo_processado WHERE processo_id = ?", Long.class, processoId);

            mockMvc.perform(get("/carga/{id}/calculos", processoId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.processoId").value(processoId))
                    .andExpect(jsonPath("$.arquivoProcessadoId").value(arquivoProcessadoId))
                    .andExpect(jsonPath("$.poligonosCalculados").value(2))
                    .andExpect(jsonPath("$.areaTotalHectares").isNumber())
                    .andExpect(jsonPath("$.poligonos[0].codigoImovel").value("CAR-A"))
                    .andExpect(jsonPath("$.poligonos[1].codigoImovel").value("CAR-B"))
                    .andExpect(jsonPath("$.intersecoes.length()").value(1))
                    .andExpect(jsonPath("$.alertasAmbientais[0].codigoImovel").value("CAR-A"))
                    .andExpect(jsonPath("$.alertasAmbientais[0].fonteCamada").value("IBAMA_EMBARGO"))
                    .andExpect(jsonPath("$.alertasAmbientais[0].codigoAlerta").value("EMB-T13"))
                    .andExpect(jsonPath("$.alertasAmbientais[0].categoria").value("ALERTA"))
                    .andExpect(jsonPath("$.totaisPorCategoria.ALERTA.quantidade").value(1))
                    .andExpect(jsonPath("$.totaisPorCategoria.APP.quantidade").value(0));

        } finally {
            jdbcTemplate.update("DELETE FROM camada_referencia WHERE identificador_origem = 'EMB-T13'");
            Files.deleteIfExists(arquivo);
            if (copia != null) {
                Files.deleteIfExists(copia);
            }
        }
    }

    @Test
    @DisplayName("Tarefa 13: carga inexistente => 404 com mensagem de erro")
    void deveDar404AoConsultarCalculosDeCargaInexistente() throws Exception {
        mockMvc.perform(get("/carga/{id}/calculos", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.erro").exists());
    }

    @Test
    @DisplayName("Tarefa 14: POST /analise/cruzamento lista as propriedades e hectares afetados das bases publicadas")
    void deveCruzarBasesPublicadas() throws Exception {
        Path arquivo = Files.createTempFile("cruzamento_analise", ".geojson");
        Path copia = null;

        jdbcTemplate.update("""
            INSERT INTO camada_referencia (fonte, nome_camada, identificador_origem, geom)
            VALUES ('IBAMA_EMBARGO', 'Embargo teste T14', 'EMB-T14',
                    ST_GeomFromText('POLYGON((-46.6335 -23.551, -46.6335 -23.552, -46.6325 -23.552,
                                              -46.6325 -23.551, -46.6335 -23.551))', 4674))
            """);

        try {
            Files.writeString(arquivo, """
                {"type":"FeatureCollection","features":[
                 {"type":"Feature","properties":{"cod_imovel":"CAR-A"},
                  "geometry":{"type":"Polygon","coordinates":[[[-46.634,-23.551],[-46.634,-23.552],
                  [-46.633,-23.552],[-46.633,-23.551],[-46.634,-23.551]]]}},
                 {"type":"Feature","properties":{"cod_imovel":"CAR-B"},
                  "geometry":{"type":"Polygon","coordinates":[[[-46.632,-23.551],[-46.632,-23.552],
                  [-46.631,-23.552],[-46.631,-23.551],[-46.632,-23.551]]]}}]}
                """);

            String hashOriginal = (java.util.UUID.randomUUID().toString() + java.util.UUID.randomUUID())
                    .replace("-", "").substring(0, 64);
            jdbcTemplate.update("""
                INSERT INTO arquivo_original
                    (processo_id, usuario_upload_id, nome_original, extensao, tipo_mime,
                     tamanho_bytes, url_armazenamento, hash_sha256)
                VALUES (?, 1, 'cruzamento_analise.geojson', 'geojson', 'application/json', ?, ?, ?)
                """, processoId, Files.size(arquivo), arquivo.toString(), hashOriginal);

            Processo processo = processoRepository.findById(processoId).orElseThrow();
            processoService.processarArquivo(processo, "cruzamento_analise.geojson", arquivo, 1L);

            copia = Path.of(jdbcTemplate.queryForObject(
                    "SELECT url_armazenamento FROM arquivo_processado WHERE processo_id = ?", String.class, processoId));

            mockMvc.perform(post("/analise/cruzamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"processoIds\":[" + processoId + "]}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.erro").exists());

            jdbcTemplate.update("""
                INSERT INTO historico_aprovacoes_gestor (processo_id, gestor_id, etapa_avaliada, decisao)
                VALUES (?, 1, 'PUBLICACAO', 'APROVADO')
                """, processoId);
            jdbcTemplate.update("""
                UPDATE processo SET versao_oficial = '1.0', versao_fechada = TRUE,
                       data_publicacao = now(), gestor_publicacao_id = 1
                 WHERE id = ?
                """, processoId);

            mockMvc.perform(post("/analise/cruzamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"processoIds\":[" + processoId + "],\"categorias\":[\"ALERTA\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.basesCruzadas").value(1))
                    .andExpect(jsonPath("$.propriedadesAfetadas").value(1))
                    .andExpect(jsonPath("$.hectaresAfetados").isNumber())
                    .andExpect(jsonPath("$.propriedades[0].processoId").value(processoId))
                    .andExpect(jsonPath("$.propriedades[0].versaoOficial").value("1.0"))
                    .andExpect(jsonPath("$.propriedades[0].codigoImovel").value("CAR-A"))
                    .andExpect(jsonPath("$.propriedades[0].areaImovelHectares").isNumber())
                    .andExpect(jsonPath("$.propriedades[0].sobreposicoes[0].fonteCamada").value("IBAMA_EMBARGO"))
                    .andExpect(jsonPath("$.propriedades[0].sobreposicoes[0].codigoAlerta").value("EMB-T14"))
                    .andExpect(jsonPath("$.totaisPorCategoria.ALERTA.quantidade").value(1));

            mockMvc.perform(post("/analise/cruzamento")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"processoIds\":[" + processoId + "],\"categorias\":[\"APP\"]}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.basesCruzadas").value(1))
                    .andExpect(jsonPath("$.propriedadesAfetadas").value(0));

        } finally {
            jdbcTemplate.update("DELETE FROM camada_referencia WHERE identificador_origem = 'EMB-T14'");
            Files.deleteIfExists(arquivo);
            if (copia != null) {
                Files.deleteIfExists(copia);
            }
        }
    }

    @Test
    @DisplayName("Tarefa 2 (Gatilho Quarentena): Deve alterar situacaoAtualId para 4 (FALHOU/Quarentena) ao falhar validação")
    @WithMockUser(username = "operador", roles = "OPERADOR")
    void deveMoverParaQuarentenaQuandoValidacaoFalhar() throws Exception {
        Long situacaoQuarentenaEsperada = 4L;

        Path arquivoInvalido = Files.createTempFile("arquivo_invalido", ".geojson");
        Files.writeString(arquivoInvalido, "Conteudo invalido para geojson");

        Processo processo = processoRepository.findById(processoId).orElseThrow();

        // O serviço lança a exceção ao tentar extrair o EPSG de um JSON inválido
        assertThrows(ResponseStatusException.class, () -> 
            processoService.processarArquivo(
                    processo, 
                    "arquivo_invalido.geojson", 
                    arquivoInvalido, 
                    1L
            )
        );

        // Garantimos que, mesmo com a exceção, o processo foi enviado para a Quarentena (4)
        Long situacaoGravada = jdbcTemplate.queryForObject(
                "SELECT situacao_atual_id FROM processo WHERE id = ?",
                Long.class, processoId);
                
        assertEquals(situacaoQuarentenaEsperada, situacaoGravada);

        Files.deleteIfExists(arquivoInvalido);
    }
}