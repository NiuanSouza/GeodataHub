-- V17: Dados iniciais para testes locais de desenvolvimento

-- 1. Inserir Órgãos
INSERT INTO orgao (nome, sigla, descricao, ativo)
VALUES
('Instituto Brasileiro de Geografia e Estatística', 'IBGE', 'Órgão de estatística', true),
('Instituto Nacional de Colonização e Reforma Agrária', 'INCRA', 'Órgão de reforma agrária', true),
('Agência Nacional de Águas', 'ANA', 'Órgão regulador de águas', true)
ON CONFLICT DO NOTHING;

-- 2. Inserir Conjuntos de Dados
INSERT INTO conjunto (nome, descricao, ativo)
VALUES
('Imóveis rurais', 'Dados de propriedades rurais', true),
('Malha municipal', 'Limites territoriais municipais', true),
('Reserva legal', 'Áreas de reserva', true),
('Uso e cobertura do solo', 'Dados de uso do solo', true),
('APP Hidrográfica', 'Área de preservação permanente', true)
ON CONFLICT DO NOTHING;

-- 3. Inserir Usuário
INSERT INTO usuario (nome, email, senha_hash, perfil_id, ativo, data_criacao)
VALUES
('Usuário Mock Frontend', 'mock@bifrost.com', 'hashfalso123', 1, true, now())
ON CONFLICT DO NOTHING;