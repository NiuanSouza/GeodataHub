-- V21: feicao_carga (depende de arquivo_processado)
-- Representa a extração (parse) dos polígonos de dentro do arquivo físico.

CREATE TABLE feicao_carga (
    id                      BIGSERIAL PRIMARY KEY,
    arquivo_processado_id   BIGINT NOT NULL REFERENCES arquivo_processado(id) ON DELETE CASCADE,
    codigo_imovel           VARCHAR(100) NOT NULL,
    geom                    geometry(Geometry, 4674) NOT NULL,
    atributos               JSONB,
    data_extracao           TIMESTAMP NOT NULL DEFAULT now(),
    
    -- Constraint (RN) para evitar polígonos duplicados para o mesmo imóvel no mesmo arquivo
    CONSTRAINT uk_feicao_carga_arquivo_imovel UNIQUE (arquivo_processado_id, codigo_imovel)
);

-- Índices B-Tree para consultas relacionais
CREATE INDEX idx_feicao_carga_imovel ON feicao_carga(codigo_imovel);

-- ÍNDICE VITAL: GiST para a geometria (Evita Table Scans na Tarefa 9)
CREATE INDEX idx_feicao_carga_geom ON feicao_carga USING GIST (geom);

COMMENT ON TABLE feicao_carga IS 
    'Tabela de trabalho. Armazena as geometrias extraídas de um arquivo_processado para viabilizar operações ST_Intersects.';

-- Função da Trigger para garantir a RN03 (Quarentena) em feicao_carga
CREATE OR REPLACE FUNCTION fn_bloquear_edicao_quarentena_feicao() 
RETURNS TRIGGER AS $$
DECLARE
    v_situacao_nome VARCHAR(50);
    v_processo_id BIGINT;
BEGIN
    -- Navega da feição até o processo para checar o status
    SELECT p.id, s.nome INTO v_processo_id, v_situacao_nome
    FROM arquivo_processado ap
    JOIN processo p ON p.id = ap.processo_id
    JOIN situacao s ON s.id = p.situacao_atual_id
    WHERE ap.id = OLD.arquivo_processado_id;

    IF v_situacao_nome = 'EM_VALIDACAO' THEN
        RAISE EXCEPTION
            'RN03: processo % está em quarentena (EM_VALIDACAO) - operação % não permitida em feicao_carga.',
            v_processo_id, TG_OP;
    END IF;
    
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

-- Trigger aplicada na tabela
CREATE TRIGGER trg_bloquear_edicao_feicao_carga
    BEFORE UPDATE OR DELETE ON feicao_carga
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloquear_edicao_quarentena_feicao();