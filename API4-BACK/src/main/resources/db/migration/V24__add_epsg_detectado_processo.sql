-- V24: Padronização e Checagem de EPSG
--guarda em uma coluna separada o EPSG que foi DETECTADO lendo o arquivo
-- espacial, para que a divergência declarado x detectado fique auditável.
--   epsg_origem    = EPSG declarado pelo operador  [já existia]
--   epsg_detectado = EPSG lido de dentro do arquivo [NOVO] ( necessário para a tarefa 9 da us06)
--   epsg_destino   = EPSG oficial da cópia de trabalho (4674)  [já existia]

ALTER TABLE processo
    ADD COLUMN IF NOT EXISTS epsg_detectado VARCHAR(20);

COMMENT ON COLUMN processo.epsg_origem IS
    'EPSG declarado pelo operador no cadastro dos metadados da carga.';
COMMENT ON COLUMN processo.epsg_detectado IS
    'EPSG detectado lendo o arquivo espacial na etapa TRATAMENTO. Fica separado do declarado para permitir auditar divergências.';
COMMENT ON COLUMN processo.epsg_destino IS
    'EPSG oficial para o qual a cópia de trabalho é convertida (SIRGAS 2000 = 4674).';