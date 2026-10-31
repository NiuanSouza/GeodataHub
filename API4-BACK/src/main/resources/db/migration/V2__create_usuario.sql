-- V2: usuario (depende de perfil)

CREATE TABLE usuario (
    id                  BIGSERIAL PRIMARY KEY,
    nome                VARCHAR(150) NOT NULL,
    email               VARCHAR(150) NOT NULL,
    senha_hash          VARCHAR(255) NOT NULL,
    perfil_id           BIGINT NOT NULL REFERENCES perfil(id),
    ativo               BOOLEAN NOT NULL DEFAULT TRUE,
    data_criacao        TIMESTAMP NOT NULL DEFAULT now(),
    data_atualizacao    TIMESTAMP,
    CONSTRAINT uq_usuario_email UNIQUE (email)
);

CREATE INDEX idx_usuario_perfil ON usuario(perfil_id);

