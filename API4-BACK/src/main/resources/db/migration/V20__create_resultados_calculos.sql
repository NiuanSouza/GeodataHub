-- V20: resultados_calculos — cache analítico das sobreposições geoespaciais
-- (US06 / Etapa 4 do pipeline). Guarda o resultado do cruzamento entre o imóvel
-- da carga e uma camada de referência, para o analista abrir o relatório sem
-- refazer o ST_Intersection.
--
-- fonte_camada + codigo_alerta_app apontam para camada_referencia
-- (fonte + identificador_origem) POR VALOR, sem FK: as camadas são recarregadas
-- em lote (DELETE + ogr2ogr) e uma FK RESTRICT impediria o reload enquanto
-- houvesse cache apontando para elas. O cache é dado derivado, pode ser refeito.

CREATE TABLE resultados_calculos (
    id                      BIGSERIAL PRIMARY KEY,

    processo_id             BIGINT NOT NULL
        REFERENCES processo(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    arquivo_processado_id   BIGINT NOT NULL
        REFERENCES arquivo_processado(id) ON DELETE RESTRICT ON UPDATE CASCADE,

    codigo_imovel           VARCHAR(100) NOT NULL,
    fonte_camada            VARCHAR(50)  NOT NULL,
    codigo_alerta_app       VARCHAR(100) NOT NULL,

    hectares_sobrepostos    NUMERIC(12, 4) NOT NULL,
    data_calculo            TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT ck_res_calc_hectares CHECK (hectares_sobrepostos >= 0),

    -- Evita duplicar o mesmo cruzamento para o mesmo arquivo. Começa por
    -- arquivo_processado_id, então também atende a FK dessa coluna.
    CONSTRAINT uk_res_calc_arquivo_imovel_alerta
        UNIQUE (arquivo_processado_id, codigo_imovel, fonte_camada, codigo_alerta_app)
);

-- Postgres não indexa FK automaticamente.
CREATE INDEX idx_res_calc_processo ON resultados_calculos(processo_id);
CREATE INDEX idx_res_calc_imovel   ON resultados_calculos(codigo_imovel);
CREATE INDEX idx_res_calc_alerta   ON resultados_calculos(fonte_camada, codigo_alerta_app);

COMMENT ON TABLE resultados_calculos IS
    'Cache analítico de sobreposições geoespaciais (imóvel x camada de referência) calculadas no PostGIS.';
COMMENT ON COLUMN resultados_calculos.codigo_imovel IS
    'Identificador original do polígono base (ex: código CAR).';
COMMENT ON COLUMN resultados_calculos.fonte_camada IS
    'Valor de camada_referencia.fonte da camada cruzada (ex: INPE_DESMATAMENTO_CERRADO).';
COMMENT ON COLUMN resultados_calculos.codigo_alerta_app IS
    'Identificador original do polígono cruzado (camada_referencia.identificador_origem).';
COMMENT ON COLUMN resultados_calculos.hectares_sobrepostos IS
    'Área da interseção em hectares, com 4 casas decimais.';