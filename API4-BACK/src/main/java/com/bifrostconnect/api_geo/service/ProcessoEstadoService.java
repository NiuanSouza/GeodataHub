package com.bifrostconnect.api_geo.service;

import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bifrostconnect.api_geo.entity.LogValidacao;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.LogValidacaoRepository;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

@Service
public class ProcessoEstadoService {
    private final JdbcTemplate jdbcTemplate;
    private final ProcessoRepository processoRepository;
    private final LogValidacaoRepository logValidacaoRepository; 

    public ProcessoEstadoService(
            JdbcTemplate jdbcTemplate, 
            ProcessoRepository processoRepository,
            LogValidacaoRepository logValidacaoRepository) {
        
        this.jdbcTemplate = jdbcTemplate;
        this.processoRepository = processoRepository;
        this.logValidacaoRepository = logValidacaoRepository;
    }

    @Transactional
    public Processo inicializarEstado(Processo processo, String etapaNome, String situacaoNome, String mensagem) {
        Long etapaId = buscarEtapaId(etapaNome);
        Long situacaoId = buscarSituacaoId(situacaoNome);
        LocalDateTime agora = LocalDateTime.now();

        jdbcTemplate.update("""
            UPDATE processo SET etapa_atual_id = ?, situacao_atual_id = ? WHERE id = ?
            """, etapaId, situacaoId, processo.getId());

        jdbcTemplate.update("""
            INSERT INTO processo_etapa
                (processo_id, etapa_id, situacao_id, data_inicio, mensagem)
            VALUES (?, ?, ?, ?, ?)
            """, processo.getId(), etapaId, situacaoId, agora, mensagem);

        processo.setEtapaAtualId(etapaId);
        processo.setSituacaoAtualId(situacaoId);
        return processo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Processo atualizarEstado(Processo processo, String etapaNome, String situacaoNome, String mensagem) {
        Long etapaId = buscarEtapaId(etapaNome);
        Long situacaoId = buscarSituacaoId(situacaoNome);

        LocalDateTime agora = LocalDateTime.now();

        jdbcTemplate.update("""
            UPDATE processo_etapa
               SET data_fim = ?, resultado = COALESCE(resultado, ?)
             WHERE processo_id = ? AND data_fim IS NULL
            """, agora, mensagem, processo.getId());

        jdbcTemplate.update("""
            UPDATE processo
               SET etapa_atual_id = ?, situacao_atual_id = ?
             WHERE id = ?
            """, etapaId, situacaoId, processo.getId());

        jdbcTemplate.update("""
            INSERT INTO processo_etapa
                (processo_id, etapa_id, situacao_id, data_inicio, mensagem)
            VALUES (?, ?, ?, ?, ?)
            """, processo.getId(), etapaId, situacaoId, agora, mensagem);

        processo.setEtapaAtualId(etapaId);
        processo.setSituacaoAtualId(situacaoId);
        return processo;
    }

    /**
     * TAREFA 14: Roteador Universal de Quarentena
     * Altera imediatamente a situação da carga para "Em validação" (Quarentena),
     * registra a etapa exata da falha, fecha o ciclo anterior na esteira e 
     * grava o log detalhado de validação.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Processo moverParaQuarentena(Long processoId, String etapaFalha, String motivoErro) {
        Processo processo = processoRepository.findById(processoId)
            .orElseThrow(() -> new IllegalArgumentException("Processo não encontrado com ID: " + processoId));

        // 1. Pausa a esteira atualizando o estado do processo usando o método existente
        // A situação "EM_VALIDACAO" funciona como a Quarentena do sistema
        processo = atualizarEstado(processo, etapaFalha, "EM_VALIDACAO", motivoErro);

        // 2. Grava na tabela física de LogValidacao
        LogValidacao log = new LogValidacao();
        log.setProcesso(processo);
        // Concatenamos a etapa na mensagem, já que não há um campo específico "etapa" na entidade
        log.setMensagem(etapaFalha + " - " + motivoErro); 
        // O campo severidade é NOT NULL, precisamos preencher
        log.setSeveridade("CRÍTICA"); 
        
        logValidacaoRepository.save(log);

        return processo;
    }

    private Long buscarEtapaId(String nome) {
        return processoRepository.buscarEtapaIdPorNome(nome)
            .orElseThrow(() -> new IllegalStateException("Etapa de domínio não encontrada: " + nome));
    }

    private Long buscarSituacaoId(String nome) {
        return processoRepository.buscarSituacaoIdPorNome(nome)
            .orElseThrow(() -> new IllegalStateException("Situação de domínio não encontrada: " + nome));
    }
}