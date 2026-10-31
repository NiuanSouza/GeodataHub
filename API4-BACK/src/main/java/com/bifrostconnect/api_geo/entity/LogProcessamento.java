package com.bifrostconnect.api_geo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "log_processamento")
@Data
public class LogProcessamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "processo_id", nullable = false)
    private Long processoId;

    @Column(name = "processo_etapa_id", nullable = false)
    private Long processoEtapaId;

    @Column(name = "nivel", nullable = false)
    private String nivel;

    @Column(name = "mensagem", nullable = false)
    private String mensagem;

    // Tarefa 15: as colunas já existiam na V8, só não estavam mapeadas.
    // Erro geoespacial que manda a carga pra Quarentena grava o código (GEO_...) e o erro do PostGIS
    @Column(name = "codigo_erro", length = 30)
    private String codigoErro;

    @Column(name = "detalhes")
    private String detalhes;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora;
}