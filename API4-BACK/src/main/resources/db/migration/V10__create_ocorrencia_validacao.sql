-- V10: ocorrencia_validacao (depende de processo, processo_etapa, regra_validacao, usuario)

CREATE TABLE ocorrencia_validacao (
    id                  BIGSERIAL PRIMARY KEY,
    mensagem            TEXT,
    data_criacao TIMESTAMP NOT NULL DEFAULT now(),
    processo_id         BIGINT NOT NULL REFERENCES processo(id),
    processo_etapa_id   BIGINT NOT NULL REFERENCES processo_etapa(id),
    regra_id            BIGINT NOT NULL REFERENCES regra_validacao(id),
    severidade          VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
    descricao           TEXT,
    detalhes            TEXT,
    data_deteccao       TIMESTAMP NOT NULL DEFAULT now(),
    data_resolucao      TIMESTAMP,
    auditor_id          BIGINT REFERENCES usuario(id),
    observacao_auditor  TEXT,
    CONSTRAINT ck_ocorrencia_severidade CHECK (severidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),
    CONSTRAINT ck_ocorrencia_status CHECK (status IN ('PENDENTE', 'EM_ANALISE', 'APROVADA', 'REJEITADA', 'CORRIGIDA'))
);

CREATE INDEX idx_ocorrencia_processo_status ON ocorrencia_validacao(processo_id, status);
