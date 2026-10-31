-- V15: Inserts provisórios para homologação da Sprint 1

-- 1. Criar os Órgãos de Teste
INSERT INTO orgao (nome, sigla, descricao, ativo) VALUES 
    ('Instituto Brasileiro de Geografia e Estatística', 'IBGE', 'Base de Malhas e Biomas', true),
    ('Instituto Nacional de Pesquisas Espaciais', 'INPE', 'Base de Desmatamento', true)
ON CONFLICT (nome) DO NOTHING;

-- 2. Criar os Conjuntos de Teste
INSERT INTO conjunto (nome, descricao, ativo) VALUES 
    ('Malha municipal', 'Divisas geopolíticas para cruzamentos', true),
    ('Uso e cobertura do solo', 'Mapeamento de florestas e pastagens', true)
ON CONFLICT (nome) DO NOTHING;

-- 3. Criar Usuários para os Perfis já existentes na V1
-- Criando um Operador (para fazer o upload da carga) e um Auditor (para a Quarentena)
INSERT INTO usuario (nome, email, senha_hash, perfil_id, ativo) VALUES 
    (
        'Vitor Samuel (Operador)', 
        'vitor.operador@bifrost.com', 
        'senha_hash_provisoria', 
        (SELECT id FROM perfil WHERE nome = 'OPERADOR'), 
        true
    ),
    (
        'Daniel Natan (Auditor)', 
        'daniel.auditor@bifrost.com', 
        'senha_hash_provisoria', 
        (SELECT id FROM perfil WHERE nome = 'AUDITOR'), 
        true
    )
ON CONFLICT (email) DO NOTHING;