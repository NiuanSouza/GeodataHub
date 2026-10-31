-- Tarefa 1 (Tabela Cofre) - Sprint 1 / US02
-- Cria a tabela que guarda o arquivo original recebido, com hash, caminho
-- físico de armazenamento e vínculo com a fonte (Processo, que registrou
-- órgão/ano/EPSG na US01).

CREATE TABLE arquivo_original (
    id                  BIGSERIAL PRIMARY KEY,

    -- "ID da fonte" herdado da US01: aponta para o Processo (a carga) que
    -- registrou órgão, ano/safra e EPSG no momento da ingestão.
    processo_id         BIGINT NOT NULL REFERENCES processo(id),

    usuario_upload_id   BIGINT NOT NULL REFERENCES usuario(id),

    nome_original       VARCHAR(255) NOT NULL,
    extensao            VARCHAR(20),
    tipo_mime           VARCHAR(100),
    tamanho_bytes       BIGINT,

    -- Caminho físico / storage (ex: bucket + chave, ou path do disco)
    url_armazenamento   TEXT NOT NULL,

    -- Hash criptográfico - UNIQUE evita reenvio duplicado do mesmo arquivo
    hash_sha256         VARCHAR(64) NOT NULL,

    imutavel            BOOLEAN NOT NULL DEFAULT TRUE,
    data_upload         TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_arquivo_original_hash UNIQUE (hash_sha256)
);

CREATE INDEX idx_arquivo_original_processo ON arquivo_original(processo_id);
CREATE INDEX idx_arquivo_original_hash ON arquivo_original(hash_sha256);

COMMENT ON TABLE arquivo_original IS
    'Tabela cofre (RN02): guarda o arquivo bruto tal como chegou. Protegida contra UPDATE/DELETE pelo trigger em V2.';

