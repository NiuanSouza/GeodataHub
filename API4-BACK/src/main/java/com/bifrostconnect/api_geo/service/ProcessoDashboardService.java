package com.bifrostconnect.api_geo.service;

import com.bifrostconnect.api_geo.dto.LogEspacialResponse;
import com.bifrostconnect.api_geo.dto.ProcessoDashboardResponse;
import com.bifrostconnect.api_geo.dto.ProcessoMetricasResponse;
import com.bifrostconnect.api_geo.repository.ProcessoDashboardRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProcessoDashboardService {

    private final ProcessoDashboardRepository repository;

    public ProcessoDashboardService(
            ProcessoDashboardRepository repository) {

        this.repository = repository;
    }

    public Page<ProcessoDashboardResponse> listarProcessos(
            LocalDate dataInicio,
            LocalDate dataFim,
            Long conjuntoId,
            Long etapaId,
            Long situacaoId,
            Pageable pageable) {

        LocalDateTime inicio = null;
        LocalDateTime fim = null;

        if (dataInicio != null) {
            inicio = dataInicio.atStartOfDay();
        }

        if (dataFim != null) {
            fim = dataFim.plusDays(1).atStartOfDay();
        }

        return repository.listarProcessos(
                inicio,
                fim,
                conjuntoId,
                etapaId,
                situacaoId,
                pageable
        );
    }

    public ProcessoMetricasResponse buscarMetricas() {

        return new ProcessoMetricasResponse(
                repository.contarTodosOsProcessos(),
                repository.contarPorSituacao(),
                repository.contarPorEtapa(),
                repository.contarPorConjunto()
        );
    }

    public List<LogEspacialResponse> buscarLogEspacial(
            Long processoId) {

        return repository.buscarLogEspacial(processoId);
    }
    
    public com.bifrostconnect.api_geo.dto.ProcessLogDetailsResponse buscarDetalhesProcessoFrontEnd(Long processoId) {
        return repository.buscarDetalhesProcessoFrontEnd(processoId);
    }
}