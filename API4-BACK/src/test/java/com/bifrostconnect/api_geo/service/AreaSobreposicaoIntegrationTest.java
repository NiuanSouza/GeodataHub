package com.bifrostconnect.api_geo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Categoria;
import com.bifrostconnect.api_geo.dto.ResultadoCalculoAnalitico.Cruzamento;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.Problema;
import com.bifrostconnect.api_geo.dto.ResultadoValidacaoTopologica.TipoProblema;
import com.bifrostconnect.api_geo.entity.ArquivoProcessado;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

// Tarefa 16: duas fazendas de 100 ha encavaladas em 20 ha passando pelo motor de verdade (PostGIS).
// Os quadrados são desenhados em metros numa projeção de área igual (LAEA) e convertidos pro
// SIRGAS 2000; o ST_Segmentize é pra borda continuar reta depois da conversão, senão a área
// escorrega alguns m². Assim o resultado esperado é conta de cabeça: 1000 x 1000 m = 100 ha
// e a faixa de 200 x 1000 m em comum = 20 ha.
// Roda em transação com rollback, igual o ExecucaoGeoespacialIntegrationTest.
// O pool com nome próprio é só pra este teste ganhar um contexto (e conexões) só dele: o
// ArquivoControllerTest faz flyway.clean() e recria o PostGIS, e as conexões abertas antes disso
// nos outros contextos ficam com os OIDs antigos em cache -> o ST_Intersects do cruzamento estoura
// "no spatial operator found for 'st_intersects'" quando a suíte inteira roda junto
@SpringBootTest
@Transactional
@TestPropertySource(properties = "spring.datasource.hikari.pool-name=t16-area-sobreposicao")
class AreaSobreposicaoIntegrationTest {

    private static final String PROJ_AREA_IGUAL =
            "+proj=laea +lat_0=-15 +lon_0=-47 +ellps=GRS80 +units=m +no_defs";

    // xmin, ymin, xmax, ymax em metros a partir do centro da projeção
    private static final String SQL_QUADRADO = """
            SELECT ST_AsGeoJSON(ST_Transform(ST_Segmentize(ST_MakeEnvelope(?, ?, ?, ?), 10), ?, 4674), 15)
            """;

    private static final BigDecimal CEM_HA = new BigDecimal("100.0000");
    private static final BigDecimal VINTE_HA = new BigDecimal("20.0000");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProcessoRepository processoRepository;

    @Autowired
    private ValidacaoTopologicaService validacaoTopologicaService;

    @Autowired
    private CalculoAnaliticoService calculoAnaliticoService;

    @TempDir
    Path pasta;

    private Processo processo;
    private ArquivoProcessado copia;
    private String fazendaA;
    private String fazendaB;

    @BeforeEach
    void montarCarga() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        fazendaA = "FAZENDA-A-" + s;
        fazendaB = "FAZENDA-B-" + s;

        Long operador = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash, perfil_id, ativo) "
                        + "VALUES (?, ?, 'hash', (SELECT id FROM perfil WHERE nome = 'ADMINISTRADOR'), TRUE) "
                        + "RETURNING id",
                Long.class, "Operador " + s, "operador." + s + "@teste.local");

        Long processoId = jdbc.queryForObject(
                "INSERT INTO processo (operador_id, conjunto_id, orgao_id, ano_safra, epsg_origem) "
                        + "VALUES (?, (SELECT min(id) FROM conjunto), (SELECT min(id) FROM orgao), '2025/2026', '4674') "
                        + "RETURNING id",
                Long.class, operador);

        // a sobreposição vira ocorrência, e a ocorrência precisa de uma etapa em andamento
        jdbc.update(
                "INSERT INTO processo_etapa (processo_id, etapa_id, situacao_id, data_inicio) "
                        + "VALUES (?, (SELECT id FROM etapa WHERE nome = 'TRATAMENTO'), "
                        + "(SELECT id FROM situacao WHERE nome = 'EM_VALIDACAO'), now())",
                processoId);

        Long original = jdbc.queryForObject(
                "INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256) "
                        + "VALUES (?, ?, 'fazendas.zip', 'teste/fazendas.zip', ?) RETURNING id",
                Long.class, processoId, operador, (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""));

        // A vai de 0 a 1000 m e B de 800 a 1800 m: os dois têm 1 km de altura
        Path geojson = pasta.resolve("fazendas.geojson");
        Files.writeString(geojson, """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"cod_imovel":"%s"},"geometry":%s},
                  {"type":"Feature","properties":{"cod_imovel":"%s"},"geometry":%s}]}
                """.formatted(fazendaA, quadrado(0, 0, 1000, 1000), fazendaB, quadrado(800, 0, 1800, 1000)));

        Long copiaId = jdbc.queryForObject(
                "INSERT INTO arquivo_processado (arquivo_original_id, processo_id, etapa_geracao_id, "
                        + "nome_arquivo, url_armazenamento, versao) "
                        + "VALUES (?, ?, (SELECT id FROM etapa WHERE nome = 'TRATAMENTO'), 'fazendas.geojson', ?, 1) "
                        + "RETURNING id",
                Long.class, original, processoId, geojson.toString());

        processo = processoRepository.findById(processoId).orElseThrow();
        copia = new ArquivoProcessado();
        copia.setId(copiaId);
        copia.setUrlArmazenamento(geojson.toString());
    }

    private String quadrado(int xmin, int ymin, int xmax, int ymax) {
        return jdbc.queryForObject(SQL_QUADRADO, String.class, xmin, ymin, xmax, ymax, PROJ_AREA_IGUAL);
    }

    @Test
    void detectaAsDuasFazendasEncavaladasEm20Hectares() throws Exception {
        ResultadoValidacaoTopologica resultado = validacaoTopologicaService.validar(processo, copia);

        assertThat(resultado.totalFeicoes()).isEqualTo(2);
        assertThat(resultado.feicoesCarregadas()).isEqualTo(2);

        // só a sobreposição: nada de geometria inválida nem duplicada
        assertThat(resultado.problemas()).hasSize(1);
        Problema sobreposicao = resultado.problemas().get(0);
        assertThat(sobreposicao.tipo()).isEqualTo(TipoProblema.POLIGONO_SOBREPOSTO);
        assertThat(sobreposicao.codigoImovel()).isEqualTo(fazendaA);
        assertThat(sobreposicao.codigoImovelRelacionado()).isEqualTo(fazendaB);
        assertThat(sobreposicao.hectaresSobrepostos()).isEqualTo(VINTE_HA);
        assertThat(resultado.problemasInaceitaveis()).containsExactly(sobreposicao);

        // e ficou gravada como ocorrência da regra de sobreposição
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ocorrencia_validacao o JOIN regra_validacao r ON r.id = o.regra_id "
                        + "WHERE o.processo_id = ? AND r.nome = 'SOBREPOSICAO_PROPRIEDADE'",
                Integer.class, processo.getId())).isEqualTo(1);
    }

    @Test
    void calculaExatamente100HectaresPorFazenda() throws Exception {
        validacaoTopologicaService.validar(processo, copia);

        ResultadoCalculoAnalitico resultado = calculoAnaliticoService.calcular(processo, copia);

        assertThat(resultado.poligonosCalculados()).isEqualTo(2);
        assertThat(resultado.areaTotalHectares()).isEqualTo(new BigDecimal("200.0000"));
        assertThat(resultado.possuiInconsistencias()).isFalse();

        // conferindo polígono a polígono direto no banco, com o mesmo ST_Area do cálculo
        assertThat(jdbc.queryForList(
                "SELECT ROUND((ST_Area(geom::geography) / 10000)::numeric, 4) FROM feicao_carga "
                        + "WHERE arquivo_processado_id = ? ORDER BY codigo_imovel",
                BigDecimal.class, copia.getId())).containsExactly(CEM_HA, CEM_HA);
    }

    // a mesma conta de interseção vale pro cruzamento com camada de referência:
    // uma APP com o desenho exato da fazenda B pega 20 ha da A e os 100 ha inteiros da B
    @Test
    void cruzamentoComAppDoTamanhoDaFazendaBDa20E100Hectares() throws Exception {
        String app = "APP-T16-" + fazendaB;
        jdbc.update(
                "INSERT INTO camada_referencia (fonte, nome_camada, identificador_origem, geom) "
                        + "VALUES ('INCAR_APP', 'APP teste T16', ?, ST_SetSRID(ST_GeomFromGeoJSON(?), 4674))",
                app, quadrado(800, 0, 1800, 1000));
        validacaoTopologicaService.validar(processo, copia);

        ResultadoCalculoAnalitico resultado = calculoAnaliticoService.calcular(processo, copia);

        // filtra pelo código da APP de teste pra não depender do que já estiver carregado na camada_referencia
        List<Cruzamento> cruzamentos = resultado.cruzamentos().stream()
                .filter(c -> app.equals(c.codigoAlerta()))
                .toList();
        assertThat(cruzamentos).extracting(Cruzamento::codigoImovel, Cruzamento::categoria, Cruzamento::hectaresSobrepostos)
                .containsExactlyInAnyOrder(
                        tuple(fazendaA, Categoria.APP, VINTE_HA),
                        tuple(fazendaB, Categoria.APP, CEM_HA));
        assertThat(resultado.possuiInconsistencias()).isFalse();

        // e o RETURNING bate com o que ficou gravado em resultados_calculos
        assertThat(jdbc.queryForList(
                "SELECT hectares_sobrepostos FROM resultados_calculos "
                        + "WHERE arquivo_processado_id = ? AND codigo_alerta_app = ? ORDER BY codigo_imovel",
                BigDecimal.class, copia.getId(), app)).containsExactly(VINTE_HA, CEM_HA);
    }
}
