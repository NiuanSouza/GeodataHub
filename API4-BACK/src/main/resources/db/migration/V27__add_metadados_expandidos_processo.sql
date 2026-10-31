-- V22: Adiciona campos de metadados expandidos (US05) na tabela processo

ALTER TABLE processo
    ADD COLUMN IF NOT EXISTS competencia VARCHAR(20),
    ADD COLUMN IF NOT EXISTS formato VARCHAR(50),
    ADD COLUMN IF NOT EXISTS classificacao_acesso VARCHAR(50),
    ADD COLUMN IF NOT EXISTS cobertura_espacial VARCHAR(255),
    ADD COLUMN IF NOT EXISTS cobertura_temporal_inicio DATE,
    ADD COLUMN IF NOT EXISTS cobertura_temporal_fim DATE;

COMMENT ON COLUMN processo.competencia IS 'Competência no formato AAAA-MM (US05)';
COMMENT ON COLUMN processo.formato IS 'Formato do arquivo de carga (ex: Shapefile, GeoJSON)';
COMMENT ON COLUMN processo.classificacao_acesso IS 'Classificação de acesso à informação';
COMMENT ON COLUMN processo.cobertura_espacial IS 'Abrangência espacial dos dados';
COMMENT ON COLUMN processo.cobertura_temporal_inicio IS 'Data de início da cobertura temporal';
COMMENT ON COLUMN processo.cobertura_temporal_fim IS 'Data de fim da cobertura temporal';

