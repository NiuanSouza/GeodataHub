package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.dto.LinhagemResponseDTO;
import com.bifrostconnect.api_geo.entity.ArquivoOriginal;
import com.bifrostconnect.api_geo.entity.LogValidacao;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ArquivoOriginalRepository;
import com.bifrostconnect.api_geo.repository.LogValidacaoRepository;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LinhagemService {

    private final ProcessoRepository processoRepository;
    private final ArquivoOriginalRepository arquivoOriginalRepository;
    
    // Injeções adicionadas para a Tarefa 15
    private final LogValidacaoRepository logValidacaoRepository;
    private final JdbcTemplate jdbcTemplate;

    public LinhagemResponseDTO buscarLinhagem(Long indicadorId) {
        // Busca o processo/indicador. Se não achar, lança 404 (Tarefa 8)
        Processo processo = processoRepository.findById(indicadorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Indicador/Processo não encontrado."));

        // Busca todos os arquivos atrelados ao processo (upload original e reprocessamentos - Tarefas 15 e 18)
        List<ArquivoOriginal> todosArquivos = arquivoOriginalRepository.findAllByProcessoIdOrderByIdAsc(processo.getId());
        if (todosArquivos.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo original da linhagem não encontrado.");
        }

        // Mapeia lista detalhada de arquivos/tentativas da linhagem
        List<LinhagemResponseDTO.ArquivoLinhagemDTO> arquivosDTO = new java.util.ArrayList<>();
        for (int i = 0; i < todosArquivos.size(); i++) {
            ArquivoOriginal arq = todosArquivos.get(i);
            String usuarioNome = null;
            if (arq.getUsuarioUploadId() != null) {
                try {
                    usuarioNome = jdbcTemplate.queryForObject(
                            "SELECT nome FROM usuario WHERE id = ?",
                            String.class,
                            arq.getUsuarioUploadId()
                    );
                } catch (Exception ignored) {}
            }
            arquivosDTO.add(LinhagemResponseDTO.ArquivoLinhagemDTO.builder()
                    .id(arq.getId())
                    .nomeArquivo(arq.getNomeOriginal())
                    .hashSha256(arq.getHashSha256())
                    .dataUpload(arq.getDataUpload())
                    .tamanhoBytes(arq.getTamanhoBytes())
                    .usuarioId(arq.getUsuarioUploadId())
                    .usuarioNome(usuarioNome)
                    .tentativa(i + 1)
                    .build());
        }

        ArquivoOriginal arquivoMaisRecente = todosArquivos.get(todosArquivos.size() - 1);

        // Busca os Logs de Quarentena gerados na Tarefa 14
        List<LogValidacao> logs = logValidacaoRepository.findByProcessoId(processo.getId());

        // Busca o Histórico de Etapas no banco de dados (Ajustado para tabelas 'etapa' e 'situacao')
        String sql = """
            SELECT e.nome as etapa, s.nome as situacao, pe.data_inicio, pe.data_fim, pe.mensagem, pe.resultado
            FROM processo_etapa pe
            JOIN etapa e ON pe.etapa_id = e.id
            JOIN situacao s ON pe.situacao_id = s.id
            WHERE pe.processo_id = ?
            ORDER BY pe.data_inicio ASC
            """;

        List<LinhagemResponseDTO.HistoricoEtapaDTO> historico = jdbcTemplate.query(sql, (rs, rowNum) -> 
            LinhagemResponseDTO.HistoricoEtapaDTO.builder()
                .etapa(rs.getString("etapa"))
                .situacao(rs.getString("situacao"))
                .dataInicio(rs.getTimestamp("data_inicio") != null ? rs.getTimestamp("data_inicio").toLocalDateTime() : null)
                .dataFim(rs.getTimestamp("data_fim") != null ? rs.getTimestamp("data_fim").toLocalDateTime() : null)
                .mensagem(rs.getString("mensagem"))
                .resultado(rs.getString("resultado"))
                .build()
        , processo.getId());

        // Identifica a Etapa e Situação atual olhando para o último registro do histórico
        String nomeEtapaAtual = "Desconhecida";
        String nomeSituacaoAtual = "Desconhecida";
        if (!historico.isEmpty()) {
            LinhagemResponseDTO.HistoricoEtapaDTO ultimaEtapa = historico.get(historico.size() - 1);
            nomeEtapaAtual = ultimaEtapa.getEtapa();
            nomeSituacaoAtual = ultimaEtapa.getSituacao();
        }

        List<LinhagemResponseDTO.LogQuarentenaDTO> logsDTO = logs.stream().map(log -> 
            LinhagemResponseDTO.LogQuarentenaDTO.builder()
                .dataDeteccao(log.getDataDeteccao())
                // Extrai a etapa do padrão salvo no ProcessoEstadoService ("Etapa - Erro")
                .etapaFalha(log.getMensagem() != null && log.getMensagem().contains(" - ") ? log.getMensagem().split(" - ")[0] : "Validação")
                .severidade(log.getSeveridade())
                .mensagemErro(log.getMensagem())
                .build()
        ).toList();

        // Busca histórico de aprovações/decisões do gestor (Tarefa 15)
        String sqlDecisoes = """
            SELECT h.id, h.gestor_id, u.nome as gestor_nome, h.etapa_avaliada, h.decisao,
                   h.parecer_justificativa, h.data_decisao
            FROM historico_aprovacoes_gestor h
            LEFT JOIN usuario u ON u.id = h.gestor_id
            WHERE h.processo_id = ?
            ORDER BY h.data_decisao ASC
            """;
        List<LinhagemResponseDTO.DecisaoGestorDTO> decisoesDTO = jdbcTemplate.query(sqlDecisoes, (rs, rowNum) ->
            LinhagemResponseDTO.DecisaoGestorDTO.builder()
                .id(rs.getLong("id"))
                .gestorId(rs.getLong("gestor_id"))
                .gestorNome(rs.getString("gestor_nome"))
                .etapa(rs.getString("etapa_avaliada"))
                .decisao(rs.getString("decisao"))
                .parecer(rs.getString("parecer_justificativa"))
                .dataDecisao(rs.getTimestamp("data_decisao") != null ? rs.getTimestamp("data_decisao").toLocalDateTime() : null)
                .build()
        , processo.getId());

        // Busca eventos de auditoria registrados (Tarefa 15: quem aprovou/reprovou, notificou operador, etc.)
        String sqlAuditoria = """
            SELECT a.id, a.usuario_id, u.nome as usuario_nome, a.acao, a.detalhes, a.data_hora
            FROM auditoria a
            LEFT JOIN usuario u ON u.id = a.usuario_id
            WHERE a.processo_id = ?
            ORDER BY a.data_hora ASC
            """;
        List<LinhagemResponseDTO.EventoAuditoriaDTO> eventosDTO = jdbcTemplate.query(sqlAuditoria, (rs, rowNum) ->
            LinhagemResponseDTO.EventoAuditoriaDTO.builder()
                .id(rs.getLong("id"))
                .usuarioId(rs.getLong("usuario_id"))
                .usuarioNome(rs.getString("usuario_nome"))
                .acao(rs.getString("acao"))
                .detalhes(rs.getString("detalhes"))
                .dataHora(rs.getTimestamp("data_hora") != null ? rs.getTimestamp("data_hora").toLocalDateTime() : null)
                .build()
        , processo.getId());

        return LinhagemResponseDTO.builder()
                .indicadorId(processo.getId())
                .processoId(processo.getId())
                .arquivoOriginalId(arquivoMaisRecente.getId())
                .nomeArquivoOriginal(arquivoMaisRecente.getNomeOriginal())
                .hashSha256(arquivoMaisRecente.getHashSha256())
                .parametrosProcessamento("Validação Espacial, Regras de Negócio Padrão")
                .dataCarga(processo.getDataCriacao())
                .statusAtual(nomeSituacaoAtual)
                .etapaAtual(nomeEtapaAtual)
                .historicoEtapas(historico)
                .logsQuarentena(logsDTO)
                .arquivos(arquivosDTO)
                .decisoesGestor(decisoesDTO)
                .eventosAuditoria(eventosDTO)
                .build();
    }
}