package com.bifrostconnect.api_geo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "arquivo_processado")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArquivoProcessado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "arquivo_original_id", nullable = false)
    private Long arquivoOriginalId;

    @Column(name = "processo_id", nullable = false)
    private Long processoId;

    @Column(name = "etapa_geracao_id", nullable = false)
    private Long etapaGeracaoId;

    @Column(name = "nome_arquivo", nullable = false, length = 255)
    private String nomeArquivo;

    @Column(name = "formato", length = 20)
    private String formato;

    @Column(name = "url_armazenamento", nullable = false, columnDefinition = "TEXT")
    private String urlArmazenamento;

    @Column(name = "hash_sha256", length = 64)
    private String hashSha256;

    @Column(name = "epsg", length = 20)
    private String epsg;

    @Column(name = "data_criacao", insertable = false, updatable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "versao", nullable = false)
    private Integer versao;
}
