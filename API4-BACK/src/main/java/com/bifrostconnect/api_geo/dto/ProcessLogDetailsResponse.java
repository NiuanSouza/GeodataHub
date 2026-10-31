package com.bifrostconnect.api_geo.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProcessLogDetailsResponse {
    private String id;
    private String dataset;
    private String stage;
    private String status;
    private String dateTime;
    private String layerName;
    private String source;
    private String year;
    private String epsg;
    private String epsgDetected;
    private Boolean versaoFechada;
    private String versaoOficial;
    private String description;
    private String integrityHash;
    private String originalFileUrl;
    private List<List<Double>> mapCoordinates;
    private Map<String, List<String>> logs;
    private String pauseReason;

    // Metadados expandidos (US05)
    private String competencia;
    private String formato;
    private String classificacaoAcesso;
    private String coberturaEspacial;
    private String coberturaTemporalInicio;
    private String coberturaTemporalFim;
    
    // Checklists e Quarentena reais
    private List<Object> validationChecks;
    private List<Object> treatmentChecks;
    private List<Object> analyticsData;
    private List<Object> quarantineRecords;
}
