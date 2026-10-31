-- V8: log_processamento (depende de processo_etapa)

CREATE TABLE log_processamento (
    id                  BIGSERIAL PRIMARY KEY,
    processo_id BIGINT REFERENCES processo(id),
    processo_etapa_id   BIGINT NOT NULL REFERENCES processo_etapa(id),
    nivel               VARCHAR(20) NOT NULL,
    mensagem            TEXT NOT NULL,
    codigo_erro         VARCHAR(30),
    detalhes            TEXT,
    data_hora           TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT ck_log_nivel CHECK (nivel IN ('INFO', 'WARNING', 'ERROR'))
);

CREATE INDEX idx_log_processo_etapa ON log_processamento(processo_etapa_id);

