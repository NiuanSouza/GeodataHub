package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CancelamentoCargaService {

    private final ProcessoRepository processoRepository;
    private final AuditoriaLogService auditoriaLogService;
    private final ProcessoEstadoService processoEstadoService;

    @Transactional
    public void cancelarCarga(
            Long processoId,
            String motivo,
            Long usuarioId) {

        if (usuarioId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O usuário responsável pelo cancelamento é obrigatório.");
        }

        if (motivo == null || motivo.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O motivo do cancelamento é obrigatório.");
        }

        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Processo não encontrado."));

        processoEstadoService.atualizarEstado(
                processo,
                "INGESTAO",
                "FALHOU",
                "Carga cancelada pelo usuário " + usuarioId
                        + ". Motivo: " + motivo.trim());

        auditoriaLogService.registrarAuditoria(
                usuarioId,
                processoId,
                "CANCELAMENTO_CARGA",
                "Carga cancelada. Motivo: " + motivo.trim(),
                null);
    }
}