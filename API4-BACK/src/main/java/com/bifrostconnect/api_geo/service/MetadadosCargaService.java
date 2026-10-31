package com.bifrostconnect.api_geo.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.bifrostconnect.api_geo.dto.MetadadosCargaRequest;
import com.bifrostconnect.api_geo.entity.Processo;
import com.bifrostconnect.api_geo.repository.ProcessoRepository;

@Service
public class MetadadosCargaService {
    private final ProcessoRepository processoRepository;
    private final ProcessoEstadoService processoEstadoService;

    public MetadadosCargaService(ProcessoRepository processoRepository, ProcessoEstadoService processoEstadoService) {
        this.processoRepository = processoRepository;
        this.processoEstadoService = processoEstadoService;
    }

    @Transactional
    public Processo salvarMetadados(MetadadosCargaRequest request) {
        validarRegrasNegocio(request);

        Processo processo = new Processo();
        processo.setConjuntoId(request.getConjuntoId());
        processo.setOrgaoId(request.getOrgaoId());
        processo.setOperadorId(request.getOperadorId());
        processo.setAnoSafra(request.getAnoSafra());
        processo.setEpsgOrigem(request.getEpsgOrigem());
        processo.setCompetencia(request.getCompetencia());
        processo.setFormato(request.getFormato());
        processo.setClassificacaoAcesso(request.getClassificacaoAcesso());
        processo.setCoberturaEspacial(request.getCoberturaEspacial());
        processo.setCoberturaTemporalInicio(request.getCoberturaTemporalInicio());
        processo.setCoberturaTemporalFim(request.getCoberturaTemporalFim());
        processo.setDataCriacao(java.time.LocalDateTime.now());

        Processo processoSalvo = processoRepository.saveAndFlush(processo);

        processoEstadoService.inicializarEstado(
                processoSalvo, "INGESTAO", "EM_ANDAMENTO",
                "Processo criado e aguardando ingestão do arquivo.");

        return processoSalvo;
    }

    private void validarRegrasNegocio(MetadadosCargaRequest request) {
        try {
            int ano = Integer.parseInt(request.getAnoSafra());
            int anoAtual = java.time.Year.now().getValue();
            if (ano < 2000 || ano > anoAtual) {
                throw new IllegalArgumentException(
                    "O ano da safra deve estar entre 2000 e " + anoAtual);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("O ano da safra deve ser um valor numérico");
        }

        if (request.getCoberturaTemporalInicio() != null
                && request.getCoberturaTemporalFim() != null
                && request.getCoberturaTemporalInicio().isAfter(request.getCoberturaTemporalFim())) {
            throw new IllegalArgumentException(
                "A cobertura temporal inicial não pode ser posterior à final");
        }

        if (request.getCompetencia() != null && !request.getCompetencia().isBlank()
                && !request.getCompetencia().matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("A competência deve estar no formato AAAA-MM");
        }

        if (request.getEpsgOrigem() != null && !request.getEpsgOrigem().equals("4674") && !request.getEpsgOrigem().equals("4326")) {
            throw new IllegalArgumentException("O EPSG de origem deve ser 4674 ou 4326");
        }
    }
}