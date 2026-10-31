package com.bifrostconnect.api_geo.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bifrostconnect.api_geo.entity.Auditoria;
import com.bifrostconnect.api_geo.entity.LogProcessamento;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.AuditoriaRepository;
import com.bifrostconnect.api_geo.repository.LogProcessamentoRepository;

@Service
public class AuditoriaLogService {

    private final LogProcessamentoRepository logRepository;
    private final AuditoriaRepository auditoriaRepository;
    private final JdbcTemplate jdbcTemplate;

    public AuditoriaLogService(
            LogProcessamentoRepository logRepository,
            AuditoriaRepository auditoriaRepository,
            JdbcTemplate jdbcTemplate) {

        this.logRepository = logRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registrarLog(Processo processo, String nivel, String mensagem) {
        registrarLog(processo, nivel, mensagem, null, null);
    }

    // Tarefa 15: mesmo log, mas com código de erro e detalhe técnico (usado pela Quarentena geoespacial)
    public void registrarLog(
            Processo processo, 
            String nivel, 
            String mensagem, 
            String codigoErro, 
            String detalhes) {

        LogProcessamento log = new LogProcessamento();

        if (processo != null && processo.getId() != null) {
            log.setProcessoId(processo.getId());

            List<Long> execucoes = jdbcTemplate.queryForList(
                    "SELECT id FROM processo_etapa " +
                    "WHERE processo_id = ? ORDER BY id DESC LIMIT 1",
                    Long.class,
                    processo.getId());

            if (!execucoes.isEmpty()) {
                log.setProcessoEtapaId(execucoes.get(0));
            } else {
                log.setProcessoEtapaId(1L);
            }
        }

        log.setNivel(nivel);
        log.setMensagem(mensagem);
        log.setCodigoErro(codigoErro);
        log.setDetalhes(detalhes);
        log.setDataHora(LocalDateTime.now());

        logRepository.save(log);
    }

    @Transactional
    public void registrarAuditoria(
            Long usuarioId,
            String acao,
            String detalhes) {

        registrarAuditoria(usuarioId, null, acao, detalhes, null);
    }

    @Transactional
    public void registrarAuditoria(
            Long usuarioId,
            Long processoId,
            String acao,
            String detalhes,
            String ip) {

        if (usuarioId == null) {
            throw new IllegalArgumentException(
                    "O usuário responsável pela ação é obrigatório.");
        }

        if (acao == null || acao.isBlank()) {
            throw new IllegalArgumentException(
                    "A ação de auditoria é obrigatória.");
        }

        Auditoria auditoria = new Auditoria();
        auditoria.setUsuarioId(usuarioId);
        auditoria.setProcessoId(processoId);
        auditoria.setAcao(acao);
        auditoria.setDetalhes(detalhes);
        auditoria.setDataHora(LocalDateTime.now());
        auditoria.setIp(ip);

        auditoriaRepository.save(auditoria);
    }
}