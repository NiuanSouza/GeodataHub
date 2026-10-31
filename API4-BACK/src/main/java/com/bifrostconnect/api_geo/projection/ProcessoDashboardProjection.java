package com.bifrostconnect.api_geo.projection;

import java.time.LocalDateTime;

public interface ProcessoDashboardProjection {

    Long getId();

    Long getOperadorId();

    Long getConjuntoId();

    Long getOrgaoId();

    String getAnoSafra();

    String getEpsgOrigem();

    Long getEtapaId();

    Long getSituacaoId();

    LocalDateTime getDataCriacao();
}