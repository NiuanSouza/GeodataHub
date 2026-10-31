-- V13: log_validacao (log espacial detalhado por carga)
-- Depende de: processo, ocorrencia_validacao (V3, V10)
--
-- Diferença em relação à ocorrencia_validacao (V10): aquela tabela registra
-- a OCORRÊNCIA da regra de negócio violada (nível "auditoria/fluxo").
-- Esta tabela guarda o DETALHE ESPACIAL do problema — a geometria (ou o
-- texto bruto, se nem foi possível parsear) que originou o problema —
-- para permitir depuração/visualização em mapa pelo auditor.
-- Se preferir não duplicar conceito, dá para remover a FK para
-- ocorrencia_validacao_id e usar log_validacao de forma independente.

CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE log_validacao (
    id                          BIGSERIAL PRIMARY KEY,

    processo_id                 BIGINT NOT NULL REFERENCES processo(id),
    ocorrencia_validacao_id     BIGINT REFERENCES ocorrencia_validacao(id),

    identificador_feicao        VARCHAR(100),

    -- Geometria problemática. Sem SRID fixo no tipo (processo aceita
    -- epsg_origem 4674 OU 4326) — o SRID efetivo é guardado em
    -- srid_origem e validado pelo CHECK abaixo.
    geom                        geometry,
    srid_origem                 INTEGER,

    geom_valida                 BOOLEAN,
    motivo_invalidez            TEXT,
    -- Texto bruto da geometria, usado quando nem foi possível fazer o
    -- parse (ex: WKT/GeoJSON corrompido) — nesse caso geom fica NULL.
    geom_bruta                  TEXT,

    severidade                  VARCHAR(20) NOT NULL,
    mensagem                    TEXT NOT NULL,

    data_deteccao               TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT ck_log_validacao_severidade
        CHECK (severidade IN ('BAIXA', 'MEDIA', 'ALTA', 'CRITICA')),

    CONSTRAINT ck_log_validacao_srid
        CHECK (geom IS NULL OR srid_origem IS NULL OR ST_SRID(geom) = srid_origem),

    -- Garante que todo log carregue alguma forma de geometria (parseada
    -- ou bruta) — não é permitido um log "espacial" sem nenhum dado espacial.
    CONSTRAINT ck_log_validacao_tem_geom
        CHECK (geom IS NOT NULL OR geom_bruta IS NOT NULL)
);

-- Postgres NÃO indexa FK automaticamente (diferente de outros SGBDs) —
-- sem isso, todo JOIN/lookup por processo_id faz seq scan.
CREATE INDEX idx_log_validacao_processo ON log_validacao(processo_id);
CREATE INDEX idx_log_validacao_ocorrencia ON log_validacao(ocorrencia_validacao_id);
CREATE INDEX idx_log_validacao_geom ON log_validacao USING GIST (geom);

COMMENT ON TABLE log_validacao IS
    'Log espacial detalhado por carga: geometria (ou texto bruto) associada a cada problema espacial detectado durante a validação de uma carga (processo).';