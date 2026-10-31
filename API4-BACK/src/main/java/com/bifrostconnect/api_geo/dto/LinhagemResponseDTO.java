package com.bifrostconnect.api_geo.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LinhagemResponseDTO {
    private Long indicadorId;
    private Long processoId;
    private Long arquivoOriginalId;
    private String nomeArquivoOriginal;
    private String hashSha256;
    private String parametrosProcessamento;
    private LocalDateTime dataCarga;
    
    // Novos campos da Tarefa 15:
    private String statusAtual;
    private String etapaAtual;
    private List<HistoricoEtapaDTO> historicoEtapas;
    private List<LogQuarentenaDTO> logsQuarentena;
    private List<ArquivoLinhagemDTO> arquivos;
    private List<DecisaoGestorDTO> decisoesGestor;
    private List<EventoAuditoriaDTO> eventosAuditoria;

    @Data
    @Builder
    public static class ArquivoLinhagemDTO {
        private Long id;
        private String nomeArquivo;
        private String hashSha256;
        private LocalDateTime dataUpload;
        private Long tamanhoBytes;
        private Long usuarioId;
        private String usuarioNome;
        private Integer tentativa;
    }

    @Data
    @Builder
    public static class DecisaoGestorDTO {
        private Long id;
        private Long gestorId;
        private String gestorNome;
        private String etapa;
        private String decisao;
        private String parecer;
        private LocalDateTime dataDecisao;
    }

    @Data
    @Builder
    public static class EventoAuditoriaDTO {
        private Long id;
        private Long usuarioId;
        private String usuarioNome;
        private String acao;
        private String detalhes;
        private LocalDateTime dataHora;
    }

    @Data
    @Builder
    public static class HistoricoEtapaDTO {
        private String etapa;
        private String situacao;
        private LocalDateTime dataInicio;
        private LocalDateTime dataFim;
        private String mensagem;
        private String resultado;
    }

    @Data
    @Builder
    public static class LogQuarentenaDTO {
        private LocalDateTime dataDeteccao;
        private String etapaFalha;
        private String severidade;
        private String mensagemErro;
    }
}