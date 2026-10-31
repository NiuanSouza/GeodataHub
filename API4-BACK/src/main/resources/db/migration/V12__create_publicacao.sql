-- V12: publicacao (depende de processo, arquivo_processado, usuario)

CREATE TABLE publicacao (
    id                      BIGSERIAL PRIMARY KEY,
    processo_id             BIGINT NOT NULL REFERENCES processo(id),
    arquivo_processado_id   BIGINT NOT NULL REFERENCES arquivo_processado(id),
    data_publicacao         TIMESTAMP NOT NULL DEFAULT now(),
    usuario_responsavel_id  BIGINT NOT NULL REFERENCES usuario(id),
    status                  VARCHAR(30) NOT NULL,
    destino                 VARCHAR(150),
    mensagem                TEXT
);

CREATE INDEX idx_publicacao_processo ON publicacao(processo_id);
