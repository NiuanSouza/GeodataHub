package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.entity.Notificacao;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.NotificacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacaoService {

    private final NotificacaoRepository notificacaoRepository;

    public NotificacaoService(NotificacaoRepository notificacaoRepository) {
        this.notificacaoRepository = notificacaoRepository;
    }

    @Transactional
    public List<Notificacao> notificarAuditores(Processo processo, String mensagem) {

        List<Long> auditores = notificacaoRepository.findUsuariosAuditoresAtivos();

        return auditores.stream()
                .map(usuarioId -> {
                    Notificacao notificacao = new Notificacao();

                    notificacao.setUsuarioId(usuarioId);
                    notificacao.setProcesso(processo);
                    notificacao.setMensagem(mensagem);
                    notificacao.setLida(false);

                    return notificacaoRepository.save(notificacao);
                })
                .toList();
    }

    public List<Notificacao> listarPorUsuario(Long usuarioId) {
        return notificacaoRepository
                .findByUsuarioIdOrderByDataCriacaoDesc(usuarioId);
    }
}