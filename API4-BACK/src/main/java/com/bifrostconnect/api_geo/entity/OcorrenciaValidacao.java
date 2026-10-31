package com.bifrostconnect.api_geo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ocorrencia_validacao")
public class OcorrenciaValidacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "regra_id", nullable = false)
    private RegraValidacao regra;

    // obrigatório no banco desde a V10: diz em qual passagem de etapa a ocorrência apareceu
    @Column(name = "processo_etapa_id", nullable = false)
    private Long processoEtapaId;

    @Column(nullable = false, length = 50)
    private String severidade;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String mensagem;

    @Column(columnDefinition = "TEXT")
    private String detalhes;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao = LocalDateTime.now();

    public OcorrenciaValidacao() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Processo getProcesso() { return processo; }
    public void setProcesso(Processo processo) { this.processo = processo; }

    public RegraValidacao getRegra() { return regra; }
    public void setRegra(RegraValidacao regra) { this.regra = regra; }

    public Long getProcessoEtapaId() { return processoEtapaId; }
    public void setProcessoEtapaId(Long processoEtapaId) { this.processoEtapaId = processoEtapaId; }

    public String getSeveridade() { return severidade; }
    public void setSeveridade(String severidade) { this.severidade = severidade; }

    public String getMensagem() { return mensagem; }
    public void setMensagem(String mensagem) { this.mensagem = mensagem; }

    public String getDetalhes() { return detalhes; }
    public void setDetalhes(String detalhes) { this.detalhes = detalhes; }

    public LocalDateTime getDataCriacao() { return dataCriacao; }
    public void setDataCriacao(LocalDateTime dataCriacao) { this.dataCriacao = dataCriacao; }
}