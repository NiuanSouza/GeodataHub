package com.bifrostconnect.api_geo.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.bifrostconnect.api_geo.dto.DecisaoGestorRequest;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

@Service
public class GestorDecisaoService {

    private final ProcessoRepository processoRepository;
    private final ProcessoEstadoService processoEstadoService;
    private final AuditoriaLogService auditoriaLogService;
    private final NotificacaoService notificacaoService;
    private final ConsolidacaoPublicacaoService consolidacaoPublicacaoService;
    private final JdbcTemplate jdbcTemplate;

    public GestorDecisaoService(
            ProcessoRepository processoRepository,
            ProcessoEstadoService processoEstadoService,
            AuditoriaLogService auditoriaLogService,
            NotificacaoService notificacaoService,
            ConsolidacaoPublicacaoService consolidacaoPublicacaoService,
            JdbcTemplate jdbcTemplate) {

        this.processoRepository = processoRepository;
        this.processoEstadoService = processoEstadoService;
        this.auditoriaLogService = auditoriaLogService;
        this.notificacaoService = notificacaoService;
        this.consolidacaoPublicacaoService = consolidacaoPublicacaoService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> processarDecisao(Long processoId, DecisaoGestorRequest request) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado com ID: " + processoId));

        if (processo.isVersaoFechada()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta versão já foi fechada/publicada e não permite novas decisões.");
        }

        String etapa = request.getEtapa().trim().toUpperCase(Locale.ROOT);
        String decisao = request.getDecisao().trim().toUpperCase(Locale.ROOT);

        if (!List.of("VALIDACAO", "TRATAMENTO", "PUBLICACAO").contains(etapa)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Etapa inválida. Deve ser VALIDACAO, TRATAMENTO ou PUBLICACAO.");
        }

        if (!List.of("APROVADO", "REPROVADO").contains(decisao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decisão inválida. Deve ser APROVADO ou REPROVADO.");
        }

        String parecer = request.getParecer() != null ? request.getParecer().trim() : null;

        if ("REPROVADO".equals(decisao) && (parecer == null || parecer.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A justificativa/parecer é obrigatória em caso de reprovação.");
        }

        // 1. Registra no histórico imutável (Tabela da V22)
        try {
            jdbcTemplate.update("""
                INSERT INTO historico_aprovacoes_gestor
                    (processo_id, gestor_id, etapa_avaliada, decisao, parecer_justificativa, data_decisao)
                VALUES (?, ?, ?, ?, ?, now())
                """, processoId, request.getGestorId(), etapa, decisao, parecer);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Erro ao gravar no histórico de aprovações: " + e.getMessage(), e);
        }

        // 2. Transições de Estado
        if ("APROVADO".equals(decisao)) {
            if ("VALIDACAO".equals(etapa)) {
                processoEstadoService.atualizarEstado(
                        processo,
                        "TRATAMENTO",
                        "EM_ANDAMENTO",
                        "Validação aprovada pelo Gestor ID " + request.getGestorId() + ". Etapa de Tratamento iniciada.");

                auditoriaLogService.registrarAuditoria(
                        request.getGestorId(),
                        "APROVACAO_GESTOR_VALIDACAO",
                        "Processo ID: " + processoId + " aprovado no gate de Validação. Parecer: " + (parecer != null ? parecer : "Aprovado sem ressalvas"));

            } else if ("TRATAMENTO".equals(etapa)) {
                try {
                    consolidacaoPublicacaoService.consolidar(processo, request.getGestorId());
                } catch (Exception e) {
                    auditoriaLogService.registrarLog(processo, "WARNING", "Consolidação de publicação gerou aviso: " + e.getMessage());
                }

                processoEstadoService.atualizarEstado(
                        processo,
                        "PUBLICACAO",
                        "EM_ANDAMENTO",
                        "Tratamento aprovado pelo Gestor ID " + request.getGestorId() + ". Etapa de Publicação e Cálculos liberada.");

                auditoriaLogService.registrarAuditoria(
                        request.getGestorId(),
                        "APROVACAO_GESTOR_TRATAMENTO",
                        "Processo ID: " + processoId + " aprovado no gate de Tratamento.");

            } else if ("PUBLICACAO".equals(etapa)) {
                processoEstadoService.atualizarEstado(
                        processo,
                        "PUBLICACAO",
                        "CONCLUIDA",
                        "Publicação oficial homologada pelo Gestor ID " + request.getGestorId() + ". Versão 1.0 fechada.");

                jdbcTemplate.update("""
                    UPDATE processo
                    SET versao_fechada = TRUE,
                        versao_oficial = '1.0',
                        data_publicacao = now(),
                        gestor_publicacao_id = ?
                    WHERE id = ?
                    """, request.getGestorId(), processoId);

                processo.setVersaoFechada(true);
                processo.setVersaoOficial("1.0");
                processo.setDataPublicacao(LocalDateTime.now());
                processo.setGestorPublicacaoId(request.getGestorId());

                auditoriaLogService.registrarAuditoria(
                        request.getGestorId(),
                        "FECHAMENTO_INDICADOR_OFICIAL",
                        "Canetada oficial aplicada ao indicador/processo ID: " + processoId + " pelo Gestor ID: " + request.getGestorId());

                try {
                    jdbcTemplate.update("""
                        UPDATE publicacao
                        SET status = 'PUBLICADO', data_publicacao = now()
                        WHERE processo_id = ?
                        """, processoId);
                } catch (Exception ignored) {
                }

                try {
                    jdbcTemplate.execute("SELECT fn_atualizar_dados_publicados()");
                } catch (Exception ignored) {
                }
            }

        } else {
            // Decisão: REPROVADO -> Envia para Quarentena (EM_VALIDACAO)
            String motivo = "Carga reprovada pelo Gestor ID " + request.getGestorId() + " na etapa " + etapa + ". Motivo: " + parecer;

            processoEstadoService.atualizarEstado(
                    processo,
                    etapa,
                    "EM_VALIDACAO",
                    motivo);

            auditoriaLogService.registrarLog(
                    processo,
                    "WARNING",
                    "Carga encaminhada para Quarentena pelo Gestor: " + parecer);

            auditoriaLogService.registrarAuditoria(
                    request.getGestorId(),
                    "REPROVACAO_GESTOR_" + etapa,
                    "Processo ID: " + processoId + " reprovado na etapa " + etapa + ". Justificativa: " + parecer);

            notificacaoService.notificarAuditores(
                    processo,
                    "Carga " + processoId + " reprovada pelo Gestor no gate " + etapa + ": " + parecer);
        }

        return Map.of(
                "sucesso", true,
                "processoId", processoId,
                "etapa", etapa,
                "decisao", decisao,
                "parecer", parecer != null ? parecer : ""
        );
    }

    @Transactional
    public Map<String, Object> alocarAuditor(Long processoId, Long auditorId, Long gestorId) {
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado com ID: " + processoId));

        Long idGestor = gestorId != null ? gestorId : 1L;

        // Registra evento na auditoria
        auditoriaLogService.registrarAuditoria(
                idGestor,
                "ALOCACAO_AUDITOR",
                "Gestor ID " + idGestor + " alocou Auditor ID " + auditorId + " para inspeção da Carga ID: " + processoId
        );

        auditoriaLogService.registrarLog(
                processo,
                "INFO",
                "Auditor ID " + auditorId + " alocado pelo Gestor ID " + idGestor + " para tratamento da carga retida na Quarentena."
        );

        // Dispara notificação específica ao auditor alocado
        jdbcTemplate.update("""
            INSERT INTO notificacao (usuario_id, processo_id, mensagem, lida, data_criacao)
            VALUES (?, ?, ?, false, now())
            """, auditorId, processoId, "Você foi designado pelo Gestor para auditar e sanar a Quarentena da carga #" + processoId);

        return Map.of(
                "sucesso", true,
                "processoId", processoId,
                "auditorId", auditorId,
                "mensagem", "Auditor alocado com sucesso e notificado."
        );
    }

    public List<Map<String, Object>> listarAuditoresAtivos() {
        return jdbcTemplate.queryForList("""
            SELECT u.id, u.nome, u.email
            FROM usuario u
            INNER JOIN perfil p ON p.id = u.perfil_id
            WHERE p.nome = 'AUDITOR' AND u.ativo = TRUE
            ORDER BY u.nome
            """);
    }

    public List<Map<String, Object>> listarDecisoesPorProcesso(Long processoId) {
        return jdbcTemplate.queryForList("""
            SELECT h.id, h.processo_id AS "processoId", h.gestor_id AS "gestorId",
                   u.nome AS "gestorNome", h.etapa_avaliada AS "etapa",
                   h.decisao, h.parecer_justificativa AS "parecer",
                   to_char(h.data_decisao, 'YYYY-MM-DD"T"HH24:MI:SS') AS "dataDecisao"
            FROM historico_aprovacoes_gestor h
            LEFT JOIN usuario u ON u.id = h.gestor_id
            WHERE h.processo_id = ?
            ORDER BY h.data_decisao DESC, h.id DESC
            """, processoId);
    }
}
