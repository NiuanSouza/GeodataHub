package com.bifrostconnect.api_geo.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

// V26: a cópia que vale é a mesma em todo lugar e o mapa se atualiza sozinho quando a versão fecha.
// Roda em transação com rollback, igual o VersaoFechadaIntegrationTest (o refresh também volta)
@SpringBootTest
@Transactional
class DadosPublicadosMapaIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    private record Cenario(long gestorId, long processoId, long copiaComPoligonos, long copiaSemPoligonos) {}

    // carga com duas cópias: v1 com polígono e cálculo, v2 (maior versão) sem nenhum polígono.
    // Era esse o caso em que a V23 antiga mostrava a carga vazia no mapa
    private Cenario criarCenario() {
        String s = UUID.randomUUID().toString().substring(0, 8);

        Long gestor = jdbc.queryForObject(
                "INSERT INTO usuario (nome, email, senha_hash, perfil_id, ativo) "
                        + "VALUES (?, ?, 'hash', (SELECT id FROM perfil WHERE nome = 'ADMINISTRADOR'), TRUE) "
                        + "RETURNING id",
                Long.class, "Gestor " + s, "gestor." + s + "@teste.local");

        Long processo = jdbc.queryForObject(
                "INSERT INTO processo (operador_id, conjunto_id, orgao_id, ano_safra, epsg_origem) "
                        + "VALUES (?, (SELECT min(id) FROM conjunto), (SELECT min(id) FROM orgao), '2025/2026', '4674') "
                        + "RETURNING id",
                Long.class, gestor);

        Long original = jdbc.queryForObject(
                "INSERT INTO arquivo_original (processo_id, usuario_upload_id, nome_original, url_armazenamento, hash_sha256) "
                        + "VALUES (?, ?, 'teste.zip', 'teste/teste.zip', ?) RETURNING id",
                Long.class, processo, gestor, (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", ""));

        long v1 = criarCopia(original, processo, 1);
        long v2 = criarCopia(original, processo, 2);

        jdbc.update(
                "INSERT INTO feicao_carga (arquivo_processado_id, codigo_imovel, geom) "
                        + "VALUES (?, 'CAR-MAPA', ST_GeomFromText('POLYGON((-47 -15, -47 -15.01, -47.01 -15.01, -47.01 -15, -47 -15))', 4674))",
                v1);

        jdbc.update(
                "INSERT INTO resultados_calculos (processo_id, arquivo_processado_id, codigo_imovel, "
                        + "fonte_camada, codigo_alerta_app, hectares_sobrepostos) "
                        + "VALUES (?, ?, 'CAR-MAPA', 'FUNAI_TERRA_INDIGENA', 'ALERTA-MAPA', 12.3456)",
                processo, v1);

        return new Cenario(gestor, processo, v1, v2);
    }

    private long criarCopia(long original, long processo, int versao) {
        return jdbc.queryForObject(
                "INSERT INTO arquivo_processado (arquivo_original_id, processo_id, etapa_geracao_id, "
                        + "nome_arquivo, url_armazenamento, versao) "
                        + "VALUES (?, ?, (SELECT id FROM etapa WHERE nome = 'TRATAMENTO'), 'tratado.geojson', 'teste/tratado.geojson', ?) "
                        + "RETURNING id",
                Long.class, original, processo, versao);
    }

    private void publicar(Cenario c) {
        jdbc.update(
                "INSERT INTO historico_aprovacoes_gestor (processo_id, gestor_id, etapa_avaliada, decisao) "
                        + "VALUES (?, ?, 'PUBLICACAO', 'APROVADO')",
                c.processoId(), c.gestorId());
        jdbc.update(
                "UPDATE processo SET versao_oficial = '1.0', versao_fechada = TRUE, data_publicacao = now(), "
                        + "gestor_publicacao_id = ? WHERE id = ?",
                c.gestorId(), c.processoId());
    }

    private List<Map<String, Object>> linhasNoMapa(long processoId) {
        return jdbc.queryForList(
                "SELECT arquivo_processado_id, codigo_imovel, hectares_sobrepostos_total "
                        + "FROM vw_dados_publicados_mapa WHERE processo_id = ?",
                processoId);
    }

    @Test
    void copiaVigenteEhAMaiorVersaoQueTemPoligonos() {
        Cenario c = criarCenario();

        Long vigente = jdbc.queryForObject(
                "SELECT arquivo_processado_id FROM vw_copia_vigente WHERE processo_id = ?",
                Long.class, c.processoId());

        assertThat(vigente).isEqualTo(c.copiaComPoligonos());
    }

    @Test
    void cargaNaoPublicadaNaoApareceNoMapa() {
        Cenario c = criarCenario();

        assertThat(linhasNoMapa(c.processoId())).isEmpty();
    }

    @Test
    void fecharVersaoAtualizaOMapaSozinhoNaCopiaVigente() {
        Cenario c = criarCenario();

        // ninguém chama fn_atualizar_dados_publicados() aqui: quem atualiza é a trigger
        publicar(c);

        List<Map<String, Object>> linhas = linhasNoMapa(c.processoId());
        assertThat(linhas).hasSize(1);
        assertThat(((Number) linhas.get(0).get("arquivo_processado_id")).longValue()).isEqualTo(c.copiaComPoligonos());
        assertThat(linhas.get(0).get("codigo_imovel")).isEqualTo("CAR-MAPA");
        assertThat((BigDecimal) linhas.get(0).get("hectares_sobrepostos_total")).isEqualByComparingTo("12.3456");
    }
}
