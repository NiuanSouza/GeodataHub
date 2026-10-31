--- V13: notificacao( depende de usuario, processo)
CREATE TABLE notificacao (

    id              BIGSERIAL PRIMARY KEY,

    usuario_id      BIGINT NOT NULL REFERENCES usuario(id),

    processo_id     BIGINT REFERENCES processo(id),

    mensagem        TEXT NOT NULL,

    lida            BOOLEAN NOT NULL DEFAULT FALSE,

    data_criacao    TIMESTAMP NOT NULL DEFAULT now()

);

CREATE INDEX idx_notificacao_usuario ON notificacao(usuario_id);

CREATE INDEX idx_notificacao_processo ON notificacao(processo_id);