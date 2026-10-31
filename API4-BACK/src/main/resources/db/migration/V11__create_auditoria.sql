-- V11: auditoria (depende de usuario, processo)

CREATE TABLE auditoria (
    id              BIGSERIAL PRIMARY KEY,
    usuario_id      BIGINT NOT NULL REFERENCES usuario(id),
    processo_id     BIGINT REFERENCES processo(id),
    acao            VARCHAR(100) NOT NULL,
    descricao       TEXT,
    detalhes        TEXT,
    data_hora       TIMESTAMP NOT NULL DEFAULT now(),
    ip              VARCHAR(45)
);

CREATE INDEX idx_auditoria_usuario_data ON auditoria(usuario_id, data_hora);