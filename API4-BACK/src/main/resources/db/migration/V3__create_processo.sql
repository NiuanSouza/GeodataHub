
-- V3: processo (depende de usuario, conjunto, orgao, etapa, situacao)

CREATE TABLE processo (
    id                  BIGSERIAL PRIMARY KEY,
    operador_id         BIGINT NOT NULL REFERENCES usuario(id),
    conjunto_id         BIGINT NOT NULL REFERENCES conjunto(id),
    orgao_id            BIGINT NOT NULL REFERENCES orgao(id),
    etapa_atual_id      BIGINT REFERENCES etapa(id),
    situacao_atual_id   BIGINT REFERENCES situacao(id),
    ano_safra           VARCHAR(10) NOT NULL,
    epsg_origem         VARCHAR(20) NOT NULL,
    epsg_destino        VARCHAR(20),
    data_criacao        TIMESTAMP NOT NULL DEFAULT now(),
    data_inicio         TIMESTAMP,
    data_fim            TIMESTAMP
);

CREATE INDEX idx_processo_conjunto ON processo(conjunto_id);
CREATE INDEX idx_processo_orgao ON processo(orgao_id);
CREATE INDEX idx_processo_operador ON processo(operador_id);
CREATE INDEX idx_processo_dashboard ON processo(conjunto_id, situacao_atual_id, data_criacao);


