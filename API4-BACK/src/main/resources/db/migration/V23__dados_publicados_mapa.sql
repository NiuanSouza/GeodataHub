-- V23: US08 — camada de produção para o mapa do Analista
-- Tarefas 20, 21, 22 e 23 (permissões).
--
-- Só entra aqui o que foi PUBLICADO: carga (processo) com versao_fechada = TRUE.
-- Pela V22, uma versão só fecha com aprovação do gestor na etapa PUBLICACAO,
-- e uma versão fechada nunca reabre. Então versao_fechada = TRUE equivale a
-- "aprovada na Publicação".

-- ==========================================
-- TAREFA 20: Materialized View de dados publicados
-- (Materialized View porque view comum não aceita índice, e a Tarefa 22 exige.)
-- ==========================================
CREATE MATERIALIZED VIEW vw_dados_publicados_mapa AS
WITH publicado AS (
    -- Se a carga tem mais de um arquivo_processado, vale o de maior versão.
    SELECT DISTINCT ON (ap.processo_id)
           ap.processo_id,
           ap.id AS arquivo_processado_id
    FROM arquivo_processado ap
    JOIN processo p ON p.id = ap.processo_id
    WHERE p.versao_fechada
    ORDER BY ap.processo_id, ap.versao DESC, ap.id DESC
),
calculo AS (
    -- resultados_calculos tem uma linha por imóvel x camada de referência.
    SELECT rc.arquivo_processado_id,
           rc.codigo_imovel,
           SUM(rc.hectares_sobrepostos) AS hectares_sobrepostos_total,
           COUNT(*)                     AS qtd_sobreposicoes
    FROM resultados_calculos rc
    WHERE rc.arquivo_processado_id IN (SELECT arquivo_processado_id FROM publicado)
    GROUP BY rc.arquivo_processado_id, rc.codigo_imovel
)
SELECT fc.id                                                    AS feicao_id,
       p.id                                                     AS processo_id,
       pb.arquivo_processado_id,
       fc.codigo_imovel,
       p.conjunto_id,
       c.nome                                                   AS conjunto,
       p.orgao_id,
       o.nome                                                   AS orgao,
       o.sigla                                                  AS orgao_sigla,
       p.ano_safra,
       p.versao_oficial,
       p.versao_fechada,
       p.data_publicacao,
       COALESCE(ca.hectares_sobrepostos_total, 0)::NUMERIC(14, 4) AS hectares_sobrepostos_total,
       COALESCE(ca.qtd_sobreposicoes, 0)::INTEGER               AS qtd_sobreposicoes,
       fc.geom
FROM publicado pb
JOIN processo p       ON p.id = pb.processo_id
JOIN conjunto c       ON c.id = p.conjunto_id
JOIN orgao o          ON o.id = p.orgao_id
JOIN feicao_carga fc  ON fc.arquivo_processado_id = pb.arquivo_processado_id
LEFT JOIN calculo ca  ON ca.arquivo_processado_id = pb.arquivo_processado_id
                     AND ca.codigo_imovel = fc.codigo_imovel
WITH DATA;

COMMENT ON MATERIALIZED VIEW vw_dados_publicados_mapa IS
    'Geometrias finais de cargas publicadas (versao_fechada = TRUE) com hectares sobrepostos e metadados de safra. Atualizar com fn_atualizar_dados_publicados() após fechar uma versão.';
COMMENT ON COLUMN vw_dados_publicados_mapa.hectares_sobrepostos_total IS
    'Soma de resultados_calculos.hectares_sobrepostos do imóvel (todas as camadas de referência cruzadas).';

-- ==========================================
-- TAREFA 22: índices
-- ==========================================
-- Obrigatório para REFRESH ... CONCURRENTLY (atualiza sem travar a leitura do mapa).
CREATE UNIQUE INDEX uq_dpm_feicao ON vw_dados_publicados_mapa(feicao_id);

-- Espacial
CREATE INDEX idx_dpm_geom ON vw_dados_publicados_mapa USING GIST (geom);

-- Filtros da tela do Analista. O composto atende qualquer combinação que
-- comece por conjunto (conjunto; conjunto+safra; conjunto+safra+órgão).
CREATE INDEX idx_dpm_conjunto_safra_orgao ON vw_dados_publicados_mapa(conjunto_id, ano_safra, orgao_id);
CREATE INDEX idx_dpm_orgao                ON vw_dados_publicados_mapa(orgao_id);
CREATE INDEX idx_dpm_processo             ON vw_dados_publicados_mapa(processo_id);
CREATE INDEX idx_dpm_versao_oficial       ON vw_dados_publicados_mapa(versao_oficial);
-- Não há índice em versao_fechada: a view só tem linhas com TRUE (um único
-- valor, o planner nunca usaria). A coluna continua exposta para a API.

-- Atualização: o back chama isto depois de fechar uma versão (ou um job agendado).
CREATE OR REPLACE FUNCTION fn_atualizar_dados_publicados()
RETURNS VOID AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY vw_dados_publicados_mapa;
END;
$$ LANGUAGE plpgsql;

REVOKE EXECUTE ON FUNCTION fn_atualizar_dados_publicados() FROM PUBLIC;

-- ==========================================
-- TAREFA 21: o banco já entrega o GeoJSON pronto
-- Retorna uma FeatureCollection. A API só repassa o texto (sem parse de geometria
-- no Java). 'truncated' = true indica que o limite foi atingido (pode haver mais
-- feições no recorte: o front deve pedir um recorte menor).
-- ST_AsGeoJSON(geom, 6): 6 casas decimais (~10 cm) reduzem o tamanho do payload.
-- ==========================================
CREATE OR REPLACE FUNCTION fn_dados_publicados_geojson(
    p_xmin        DOUBLE PRECISION,
    p_ymin        DOUBLE PRECISION,
    p_xmax        DOUBLE PRECISION,
    p_ymax        DOUBLE PRECISION,
    p_conjunto_id BIGINT  DEFAULT NULL,
    p_ano_safra   VARCHAR DEFAULT NULL,
    p_orgao_id    BIGINT  DEFAULT NULL,
    p_limite      INTEGER DEFAULT 5000
) RETURNS JSON AS $$
    SELECT json_build_object(
               'type', 'FeatureCollection',
               'truncated', COUNT(*) >= p_limite,
               'features', COALESCE(json_agg(f.feature), '[]'::json)
           )
    FROM (
        SELECT json_build_object(
                   'type', 'Feature',
                   'id', d.feicao_id,
                   'geometry', ST_AsGeoJSON(d.geom, 6)::json,
                   'properties', json_build_object(
                       'processo_id', d.processo_id,
                       'codigo_imovel', d.codigo_imovel,
                       'conjunto', d.conjunto,
                       'orgao', d.orgao,
                       'orgao_sigla', d.orgao_sigla,
                       'ano_safra', d.ano_safra,
                       'versao_oficial', d.versao_oficial,
                       'data_publicacao', d.data_publicacao,
                       'hectares_sobrepostos_total', d.hectares_sobrepostos_total,
                       'qtd_sobreposicoes', d.qtd_sobreposicoes
                   )
               ) AS feature
        FROM vw_dados_publicados_mapa d
        WHERE ST_Intersects(d.geom, ST_MakeEnvelope(p_xmin, p_ymin, p_xmax, p_ymax, 4674))
          AND (p_conjunto_id IS NULL OR d.conjunto_id = p_conjunto_id)
          AND (p_ano_safra   IS NULL OR d.ano_safra   = p_ano_safra)
          AND (p_orgao_id    IS NULL OR d.orgao_id    = p_orgao_id)
        LIMIT p_limite
    ) f;
$$ LANGUAGE sql STABLE;

COMMENT ON FUNCTION fn_dados_publicados_geojson(DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT, VARCHAR, BIGINT, INTEGER) IS
    'FeatureCollection GeoJSON (SRID 4674) das geometrias publicadas dentro do recorte (xmin, ymin, xmax, ymax), com filtros opcionais de conjunto, safra e órgão.';

-- ==========================================
-- TAREFA 23: role de leitura restrita do Analista
-- Sem senha no Git (lição da V19). Definir fora do repositório:
--   ALTER ROLE analista_mapa PASSWORD '...';
-- ==========================================
DO
$do$
BEGIN
   IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'analista_mapa') THEN
      CREATE ROLE analista_mapa LOGIN;
   END IF;
END
$do$;

GRANT USAGE ON SCHEMA public TO analista_mapa;

-- Começa do zero: nenhum acesso a tabela, e só então libera o necessário.
REVOKE ALL ON ALL TABLES IN SCHEMA public FROM analista_mapa;

GRANT SELECT ON vw_dados_publicados_mapa TO analista_mapa;
GRANT SELECT ON camada_referencia        TO analista_mapa;
GRANT EXECUTE ON FUNCTION fn_dados_publicados_geojson(DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, DOUBLE PRECISION, BIGINT, VARCHAR, BIGINT, INTEGER) TO analista_mapa;

-- Defesa em camadas: mesmo que alguém conceda escrita por engano no futuro,
-- as sessões desta role abrem somente-leitura.
ALTER ROLE analista_mapa SET default_transaction_read_only = on;