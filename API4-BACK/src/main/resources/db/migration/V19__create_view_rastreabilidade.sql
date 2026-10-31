-- ==========================================
-- TAREFA 10: VIEW DE RASTREABILIDADE
-- ==========================================
CREATE OR REPLACE VIEW vw_linhagem_indicadores AS
SELECT 
    p.id AS processo_id,
    -- Como a V3 não tem numero_car, usamos o id do processo como identificador base
    p.id AS identificador_imovel, 
    p.data_criacao AS data_processamento,
    
    -- Arquivo Origem
    a.nome_original AS nome_arquivo,
    a.url_armazenamento AS caminho_arquivo,
    
    -- Logs de Validação (Ocorrências)
    ov.severidade AS status_validacao,
    ov.mensagem AS detalhe_alerta,
    
    -- Regra Aplicada (Metadado)
    rv.nome AS regra_aplicada,
    rv.tipo AS categoria_regra
    
FROM processo p
LEFT JOIN arquivo_original a ON a.processo_id = p.id
LEFT JOIN ocorrencia_validacao ov ON p.id = ov.processo_id
LEFT JOIN regra_validacao rv ON ov.regra_id = rv.id;


-- ==========================================
-- TAREFA 11: CREDENCIAIS DE LEITURA (AUDITOR)
-- ==========================================

-- 1. Cria a Role (perfil) se ela não existir
DO
$do$
BEGIN
   IF NOT EXISTS (
      SELECT FROM pg_catalog.pg_roles
      WHERE  rolname = 'auditor_ambiental') THEN

      CREATE ROLE auditor_ambiental LOGIN PASSWORD 'auditor123';
   END IF;
END
$do$;

-- 2. Concede permissão de uso no schema public
GRANT USAGE ON SCHEMA public TO auditor_ambiental;

-- 3. Concede estritamente permissão de SELECT em TODAS as tabelas e na view
GRANT SELECT ON ALL TABLES IN SCHEMA public TO auditor_ambiental;
GRANT SELECT ON vw_linhagem_indicadores TO auditor_ambiental;

-- 4. Garante que tabelas futuras também tenham permissão apenas de SELECT para o auditor
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO auditor_ambiental;