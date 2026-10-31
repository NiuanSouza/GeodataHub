package com.bifrostconnect.api_geo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "processo")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Processo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(name = "data_criacao")
    private LocalDateTime dataCriacao = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (this.dataCriacao == null) {
            this.dataCriacao = LocalDateTime.now();
        }
    }

    @Column(name = "ano_safra")
    private String anoSafra;

    @Column(name = "epsg_origem")
    private String epsgOrigem;

    @Column(name = "epsg_detectado")
    private String epsgDetectado;

    @Column(name = "epsg_destino")
    private String epsgDestino;

    @Column(name = "data_inicio")
    private LocalDateTime dataInicio;

    @Column(name = "data_fim")
    private LocalDateTime dataFim;

    @Column(name = "orgao_id")
    private Long orgaoId;

    @Column(name = "conjunto_id")
    private Long conjuntoId;

    @Column(name = "operador_id")
    private Long operadorId;

    @Column(name = "situacao_atual_id")
    private Long situacaoAtualId;

    @Column(name = "etapa_atual_id")
    private Long etapaAtualId;

    // Metadados expandidos (US05)
    @Column(name = "competencia")
    private String competencia;

    @Column(name = "formato")
    private String formato;

    @Column(name = "classificacao_acesso")
    private String classificacaoAcesso;

    @Column(name = "cobertura_espacial")
    private String coberturaEspacial;

    @Column(name = "cobertura_temporal_inicio")
    private java.time.LocalDate coberturaTemporalInicio;

    @Column(name = "cobertura_temporal_fim")
    private java.time.LocalDate coberturaTemporalFim;

    // Versionamento e Congelamento (US07 / V22)
    @Column(name = "versao_oficial")
    private String versaoOficial;

    @Column(name = "versao_fechada")
    private boolean versaoFechada;

    @Column(name = "data_publicacao")
    private LocalDateTime dataPublicacao;

    @Column(name = "gestor_publicacao_id")
    private Long gestorPublicacaoId;
}