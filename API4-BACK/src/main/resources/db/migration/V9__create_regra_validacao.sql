-- V9: regra_validacao (sem dependências)

CREATE TABLE regra_validacao (
    id          BIGSERIAL PRIMARY KEY,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    nome        VARCHAR(100) NOT NULL,
    descricao   VARCHAR(255),
    tipo        VARCHAR(50),
    severidade  VARCHAR(20) NOT NULL,
    ativa       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_regra_validacao_nome UNIQUE (nome),
    CONSTRAINT ck_regra_severidade CHECK (severidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA'))
);


INSERT INTO regra_validacao (nome, descricao, tipo, severidade) VALUES
    ('CAR_DUPLICADO', 'CAR ja existente na base corporativa', 'DUPLICIDADE', 'CRITICA'),
    ('GEOMETRIA_INVALIDA', 'Poligono com geometria invalida', 'GEOMETRIA', 'ALTA'),
    ('SOBREPOSICAO_PROPRIEDADE', 'Poligono sobreposto a outra propriedade', 'TOPOLOGIA', 'CRITICA'),
    ('SOBREPOSICAO_TERRA_INDIGENA', 'Poligono sobreposto a terra indigena', 'TOPOLOGIA', 'CRITICA'),
    ('EPSG_INVALIDO', 'Sistema de coordenadas nao reconhecido', 'METADADO', 'MEDIA');

