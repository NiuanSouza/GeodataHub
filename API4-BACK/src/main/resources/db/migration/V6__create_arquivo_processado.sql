-- V6: arquivo_processado (depende de arquivo_original, processo, etapa)

CREATE TABLE arquivo_processado (
    id                      BIGSERIAL PRIMARY KEY,
    arquivo_original_id     BIGINT NOT NULL REFERENCES arquivo_original(id),
    processo_id             BIGINT NOT NULL REFERENCES processo(id),
    etapa_geracao_id        BIGINT NOT NULL REFERENCES etapa(id),
    nome_arquivo            VARCHAR(255) NOT NULL,
    formato                 VARCHAR(20),
    url_armazenamento       TEXT NOT NULL,
    hash_sha256             VARCHAR(64),
    epsg                    VARCHAR(20),
    data_criacao            TIMESTAMP NOT NULL DEFAULT now(),
    versao                  INTEGER NOT NULL
);

CREATE INDEX idx_arquivo_processado_original ON arquivo_processado(arquivo_original_id);
CREATE INDEX idx_arquivo_processado_processo ON arquivo_processado(processo_id);
