 package com.bifrostconnect.api_geo.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;


public class MetadadosCargaRequest {

@NotNull(message = "O operador é obrigatório")
private Long operadorId;    

@NotNull(message = "O órgão é obrigatório")

private Long orgaoId;



@NotNull(message = "O conjunto é obrigatório")
private Long conjuntoId;; // <--- Adicionado para preencher a coluna not-null do banco


@NotBlank(message = "O ano da safra é obrigatório")

private String anoSafra;


@NotBlank(message = "O EPSG de origem é obrigatório")

private String epsgOrigem;


public MetadadosCargaRequest() {

}


public MetadadosCargaRequest(Long operadorId, Long orgaoId, Long conjuntoId, String anoSafra, String epsgOrigem) {

this.operadorId = operadorId;

this.orgaoId = orgaoId;

this.conjuntoId = conjuntoId;

this.anoSafra = anoSafra;

this.epsgOrigem = epsgOrigem;

}


public Long getOrgaoId() {

return orgaoId;

}


public void setOrgaoId(Long orgaoId) {

this.orgaoId = orgaoId;

}


public Long getConjuntoId() {

return conjuntoId;

}


public void setConjuntoId(Long conjuntoId) {

this.conjuntoId = conjuntoId;

}


public String getAnoSafra() {

return anoSafra;

}


public void setAnoSafra(String anoSafra) {

this.anoSafra = anoSafra;

}


public String getEpsgOrigem() {

return epsgOrigem;

}


public void setEpsgOrigem(String epsgOrigem) {

this.epsgOrigem = epsgOrigem;

}

// Metadados expandidos (US05) - opcionais para manter compatibilidade com clientes antigos
private String competencia;
private String formato;
private String classificacaoAcesso;
private String coberturaEspacial;
private java.time.LocalDate coberturaTemporalInicio;
private java.time.LocalDate coberturaTemporalFim;

public String getCompetencia() { return competencia; }
public void setCompetencia(String competencia) { this.competencia = competencia; }
public String getFormato() { return formato; }
public void setFormato(String formato) { this.formato = formato; }
public String getClassificacaoAcesso() { return classificacaoAcesso; }
public void setClassificacaoAcesso(String classificacaoAcesso) { this.classificacaoAcesso = classificacaoAcesso; }
public String getCoberturaEspacial() { return coberturaEspacial; }
public void setCoberturaEspacial(String coberturaEspacial) { this.coberturaEspacial = coberturaEspacial; }
public java.time.LocalDate getCoberturaTemporalInicio() { return coberturaTemporalInicio; }
public void setCoberturaTemporalInicio(java.time.LocalDate d) { this.coberturaTemporalInicio = d; }
public java.time.LocalDate getCoberturaTemporalFim() { return coberturaTemporalFim; }
public void setCoberturaTemporalFim(java.time.LocalDate d) { this.coberturaTemporalFim = d; }

public Long getOperadorId() {
    return operadorId;
}

public void setOperadorId(Long operadorId) {
    this.operadorId = operadorId;
}

} 