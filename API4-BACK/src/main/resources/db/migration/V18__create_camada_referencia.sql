-- V18: camada_referencia — camadas de apoio (geodados oficiais de terceiros:
-- IBAMA, FUNAI, IBGE, INCRA, ICMBio, INPE, SFB, INCAR/SICAR) usadas pela
-- validação espacial de uma carga (ex: ST_Intersects entre a propriedade
-- submetida e uma terra indígena/UC/área embargada).
--
-- NÃO é o mesmo conceito de "carga" (processo/arquivo_original/
-- arquivo_processado) — isso aqui é dado de referência estático/periódico,
-- carregado em lote via ogr2ogr (fora da API), não pelo fluxo de upload do
-- operador.
--
-- Tabela única e genérica (em vez de uma tabela por fonte) porque cada
-- fonte tem um schema de atributos totalmente diferente no DBF/CSV
-- original — os atributos variáveis vão em JSONB; só o que é comum a
-- todas as fontes (geometria, origem, referência temporal) vira coluna.
--
-- Mapeamento fonte -> arquivo no Drive (pasta GEOESPACIAIS), pra usar como
-- referência na Fase E (carga em lote):
--   INCAR_AREA_IMOVEL            -> INCAR/AREA_IMOVEL.zip
--   SFB_FLORESTA_PUBLICA         -> SFB (Florestas Públicas)/CNFP_2025.zip
--   MMA_UC_CNUC                  -> MMA (Unidades de Conservação)/cnuc_2026_07.csv  [ver nota abaixo]
--   INCRA_QUILOMBOLA             -> INCRA/Áreas de Quilombolas.zip
--   INCRA_ASSENTAMENTO           -> INCRA/Assentamento Brasil.zip
--   ICMBIO_UC_FEDERAL            -> ICMBio/copy_of_limite_ucs_federais_082026.zip
--   IBGE_MUNICIPIO                -> IBGE/PR_Municipios_2025.zip
--   IBGE_BIOMA_250MIL             -> IBGE/Biomas_250mil.zip
--   IBGE_BIOMA_5000MIL            -> IBGE/Biomas_5000mil.zip
--   INPE_DESMATAMENTO_MATA_ATLANTICA -> INPE/yearly_deforestation_biome_mata_atlantica...zip
--   INPE_DESMATAMENTO_CERRADO    -> INPE/yearly_deforestation_biome_cerrado...zip
--   INPE_QUEIMADA                -> INPE/bdqueimadas_2025-09-01_2026-09-02.zip
--   IBAMA_EMBARGO                -> IBAMA/adm_embargos_ibama_a(PARANA).zip
--   FUNAI_TERRA_INDIGENA         -> FUNAI/tis_poligonais.zip
--
-- NOTA: MMA_UC_CNUC é .csv, não shapefile — ainda não confirmamos se tem
-- coluna de geometria (WKT/lat-lon) ou se é só atributo tabular que precisa
-- ser cruzado com outra camada de limites de UC. Investigar antes da carga.

CREATE TABLE camada_referencia (
    id                      BIGSERIAL PRIMARY KEY,

    fonte                   VARCHAR(50) NOT NULL,
    nome_camada             VARCHAR(150) NOT NULL,
    identificador_origem    VARCHAR(100),

    geom                    geometry(Geometry, 4674) NOT NULL,
    atributos               JSONB,

    data_referencia         DATE,
    data_carga              TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_camada_referencia_fonte ON camada_referencia(fonte);
CREATE INDEX idx_camada_referencia_geom ON camada_referencia USING GIST (geom);
CREATE INDEX idx_camada_referencia_atributos ON camada_referencia USING GIN (atributos);

COMMENT ON TABLE camada_referencia IS
    'Camadas de referência de terceiros (IBAMA, FUNAI, IBGE, INCRA, ICMBio, INPE, SFB, INCAR/SICAR) usadas na validação espacial das cargas. Carga em lote via ogr2ogr, fora do fluxo de upload do operador.';