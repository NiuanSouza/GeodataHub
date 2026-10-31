package com.bifrostconnect.api_geo.projection;

import java.time.LocalDateTime;

public interface LogEspacialProjection {

    Long getId();

    Long getProcessoEtapaId();

    Long getEtapaId();

    Long getSituacaoId();

    Integer getTentativa();

    String getNivel();

    String getMensagem();

    String getCodigoErro();

    String getDetalhes();

    LocalDateTime getDataHora();
}