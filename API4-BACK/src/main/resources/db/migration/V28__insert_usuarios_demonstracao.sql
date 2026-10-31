-- V28: Garante a existência dos usuários de demonstração de todos os perfis

INSERT INTO usuario (id, nome, email, senha_hash, perfil_id, ativo, data_criacao)
VALUES 
    (3, 'Maria Oliveira (Analista)', 'analista@bifrost.com', 'Troca123', 3, true, now()),
    (4, 'Gestor API4', 'admin@bifrost.com', 'Troca123', 4, true, now())
ON CONFLICT (id) DO UPDATE SET 
    nome = EXCLUDED.nome,
    perfil_id = EXCLUDED.perfil_id,
    ativo = EXCLUDED.ativo;

SELECT setval('usuario_id_seq', (SELECT MAX(id) FROM usuario));

