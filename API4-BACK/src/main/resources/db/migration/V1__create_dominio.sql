-- V1: tabelas de domínio (sem dependências de FK entre si)

CREATE TABLE perfil (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(50) NOT NULL,
    descricao   VARCHAR(255),
    CONSTRAINT uq_perfil_nome UNIQUE (nome)
);

CREATE TABLE orgao (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(150) NOT NULL,
    sigla       VARCHAR(20),
    descricao   VARCHAR(255),
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_orgao_nome UNIQUE (nome)
);

CREATE TABLE conjunto (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(100) NOT NULL,
    descricao   VARCHAR(255),
    ativo       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_conjunto_nome UNIQUE (nome)
);

CREATE TABLE etapa (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(50) NOT NULL,
    ordem       INTEGER NOT NULL,
    descricao   VARCHAR(255),
    CONSTRAINT uq_etapa_nome UNIQUE (nome)
);

CREATE TABLE situacao (
    id          BIGSERIAL PRIMARY KEY,
    nome        VARCHAR(50) NOT NULL,
    descricao   VARCHAR(255),
    CONSTRAINT uq_situacao_nome UNIQUE (nome)
);

-- Dados fixos de domínio (parte do sistema, não "dado de uso" - por isso é migration)
INSERT INTO perfil (nome, descricao) VALUES
    ('OPERADOR', 'Responsavel pela entrada das cargas'),
    ('AUDITOR', 'Responsavel por tratar problemas de validacao'),
    ('ANALISTA', 'Consome dados publicados para analise'),
    ('ADMINISTRADOR', 'Gerenciamento administrativo do sistema');

INSERT INTO etapa (nome, ordem, descricao) VALUES
    ('INGESTAO', 1, 'Recebimento e hash do arquivo'),
    ('TRATAMENTO', 2, 'Padronizacao e limpeza automatizada'),
    ('VALIDACAO', 3, 'Motor de regras / quarentena'),
    ('CALCULO_ANALITICO', 4, 'Geracao de metricas e cruzamentos'),
    ('PUBLICACAO', 5, 'Disponibilizacao no banco corporativo');

INSERT INTO situacao (nome, descricao) VALUES
    ('EM_ANDAMENTO', 'Etapa sendo executada'),
    ('EM_VALIDACAO', 'Aguardando intervencao do auditor'),
    ('COM_RESSALVA', 'Concluida com aviso nao-impeditivo'),
    ('FALHOU', 'Erro tecnico intransponivel'),
    ('CONCLUIDA', 'Executada com exito');
