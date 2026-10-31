package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CatalogoBasesService {

    private final JdbcTemplate jdbcTemplate;

    // US09 - Tarefa 7: trava de blindagem do catálogo. Fica num lugar só pra listagem e
    // detalhe usarem exatamente a mesma regra. Além de Publicação + Concluída, exige a
    // versão fechada, igual à trava do mapa (Tarefa 15): sem aprovação do gestor não é base oficial
    private static final String FILTRO_BLINDAGEM = """
            WHERE e.nome = 'PUBLICACAO'
              AND s.nome = 'CONCLUIDA'
              AND p.versao_fechada = TRUE
            """;

    private static final String SELECT_BASE = """
            SELECT p.id, p.ano_safra, p.versao_oficial, p.data_publicacao,
                   p.epsg_destino,
                   c.id AS conjunto_id, c.nome AS conjunto,
                   o.id AS orgao_id, o.nome AS orgao, o.sigla AS orgao_sigla,
                   (SELECT COUNT(*) FROM feicao_carga fc
                      JOIN arquivo_processado ap ON fc.arquivo_processado_id = ap.id
                     WHERE ap.processo_id = p.id) AS total_feicoes
            FROM processo p
            JOIN etapa e ON e.id = p.etapa_atual_id
            JOIN situacao s ON s.id = p.situacao_atual_id
            LEFT JOIN conjunto c ON c.id = p.conjunto_id
            LEFT JOIN orgao o ON o.id = p.orgao_id
            """;

    public List<Map<String, Object>> listarBasesPublicadas(Long conjuntoId, String anoSafra) {
        StringBuilder sql = new StringBuilder(SELECT_BASE).append(FILTRO_BLINDAGEM);
        List<Object> params = new ArrayList<>();

        if (conjuntoId != null) {
            sql.append(" AND p.conjunto_id = ?");
            params.add(conjuntoId);
        }

        if (anoSafra != null && !anoSafra.isBlank()) {
            sql.append(" AND p.ano_safra = ?");
            params.add(anoSafra);
        }

        sql.append(" ORDER BY p.data_publicacao DESC NULLS LAST, p.id DESC");

        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    public Map<String, Object> buscarDetalhesBase(Long idBase) {
        // a trava vai junto na consulta do detalhe: base que existe mas não foi publicada
        // devolve o mesmo 404 da inexistente, pra não entregar pro Analista que ela existe
        List<Map<String, Object>> resultado = jdbcTemplate.queryForList(
                SELECT_BASE + FILTRO_BLINDAGEM + " AND p.id = ?", idBase);

        if (resultado.isEmpty()) {
            throw new RecursoNaoEncontradoException("Base " + idBase + " não encontrada no catálogo.");
        }

        return resultado.get(0);
    }
}
