-- V7: processo_etapa (histórico - depende de processo, etapa, situacao)
CREATE TABLE processo_etapa (
    id              BIGSERIAL PRIMARY KEY,
    processo_id     BIGINT NOT NULL REFERENCES processo(id),
    etapa_id        BIGINT NOT NULL REFERENCES etapa(id),
    situacao_id     BIGINT NOT NULL REFERENCES situacao(id),
    data_inicio     TIMESTAMP NOT NULL,
    data_fim        TIMESTAMP,
    tentativa       INTEGER NOT NULL DEFAULT 1,
    mensagem        TEXT,
    resultado       TEXT
);

CREATE INDEX idx_processo_etapa_processo ON processo_etapa(processo_id, etapa_id);