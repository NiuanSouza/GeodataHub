package com.bifrostconnect.api_geo.controller;

import com.bifrostconnect.api_geo.dto.NotificacaoResponse;
import com.bifrostconnect.api_geo.entity.Notificacao;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.service.NotificacaoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    @PostMapping("/auditor")
    public ResponseEntity<List<NotificacaoResponse>> notificarAuditores(
            @RequestBody NotificacaoRequest request) {

        Processo processo = new Processo();
        processo.setId(request.processoId());

        List<Notificacao> notificacoes =
                notificacaoService.notificarAuditores(
                        processo,
                        request.mensagem()
                );

        List<NotificacaoResponse> resposta = notificacoes.stream()
                .map(notificacao -> new NotificacaoResponse(
                        notificacao.getId(),
                        notificacao.getUsuarioId(),
                        notificacao.getProcesso().getId(),
                        notificacao.getMensagem(),
                        notificacao.getLida(),
                        notificacao.getDataCriacao()
                ))
                .toList();

        return ResponseEntity.ok(resposta);
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<List<NotificacaoResponse>> listarPorUsuario(
            @PathVariable Long usuarioId) {

        List<Notificacao> notificacoes =
                notificacaoService.listarPorUsuario(usuarioId);

        List<NotificacaoResponse> resposta = notificacoes.stream()
                .map(notificacao -> new NotificacaoResponse(
                        notificacao.getId(),
                        notificacao.getUsuarioId(),
                        notificacao.getProcesso() != null
                                ? notificacao.getProcesso().getId()
                                : null,
                        notificacao.getMensagem(),
                        notificacao.getLida(),
                        notificacao.getDataCriacao()
                ))
                .toList();

        return ResponseEntity.ok(resposta);
    }

    public record NotificacaoRequest(
            Long processoId,
            String mensagem
    ) {
    }
}
