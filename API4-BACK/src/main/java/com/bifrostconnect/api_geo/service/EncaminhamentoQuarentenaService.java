package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EncaminhamentoQuarentenaService {

    private final ProcessoRepository processoRepository;
    private final AuditoriaLogService auditoriaLogService;

    public void notificarOperador(Long processoId, Long usuarioId) {
        // 1. Busca o processo ou dispara 404 se não existir
        Processo processo = processoRepository.findById(processoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Processo não encontrado."));

        // 2. Aqui você pode atualizar o estado/etapa do processo se necessário, 
        // por exemplo, mudando a situação para indicar que está apto para reprocessamento pelo operador.
        
        // 3. Registra o evento na auditoria
        auditoriaLogService.registrarAuditoria(
                usuarioId,
                "NOTIFICAR_OPERADOR_QUARENTENA",
                "Auditor inspecionou a quarentena e encaminhou o processo ID " + processoId + " para reprocessamento."
        );
    }
}