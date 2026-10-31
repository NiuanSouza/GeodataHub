package com.bifrostconnect.api_geo.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "arquivo_original")
@Data
@NoArgsConstructor
public class ArquivoOriginal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Relacionamento com o Processo
    @ManyToOne
    @JoinColumn(name = "processo_id", nullable = false)
    private Processo processo;

    // Campo obrigatório preenchido diretamente pelo Service
    @Column(name = "usuario_upload_id", nullable = false)
    private Long usuarioUploadId;

    // Metadados do Arquivo
    @Column(name = "nome_original", nullable = false)
    private String nomeOriginal;

    @Column(name = "extensao", length = 20)
    private String extensao;

    @Column(name = "tipo_mime", length = 100)
    private String tipoMime;

    @Column(name = "tamanho_bytes", nullable = false)
    private Long tamanhoBytes;

    @Column(name = "url_armazenamento", nullable = false, columnDefinition = "TEXT")
    private String urlArmazenamento;

    // US02 - Comprovante de Integridade
    @Column(name = "hash_sha256", nullable = false, unique = true, length = 64)
    private String hashSha256;

    @Column(name = "imutavel", nullable = false)
    private Boolean imutavel = true;

    // Deixando o banco cuidar do timestamp de inserção para evitar conflitos de null
    @Column(name = "data_upload", insertable = false, updatable = false)
    private LocalDateTime dataUpload;
}