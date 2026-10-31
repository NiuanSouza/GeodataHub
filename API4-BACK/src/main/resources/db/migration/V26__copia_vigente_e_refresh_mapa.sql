-- V26: uma regra só pra "qual cópia vale" + mapa se atualizando sozinho
--
-- Problema 1: a carga pode ter mais de um arquivo_processado (versão 1, 2...) e cada parte
-- escolhia de um jeito: a V23 pegava a maior versão (mesmo sem polígonos, aí o mapa mostrava
-- a carga vazia) e a Tarefa 13 pegava o maior id com polígonos. Agora as duas regras viram
-- uma só, aqui no banco, e o mapa (V23), a Tarefa 13 e a Tarefa 14 leem daqui.
--
-- Problema 2: a vw_dados_publicados_mapa só atualiza com fn_atualizar_dados_publicados() e
-- ninguém chamava. Agora uma trigger chama quando a versão fecha.

-- ==========================================
-- Cópia vigente de cada carga: maior versão entre as que têm polígonos extraídos
-- (cópia sem feicao_carga é cópia que não passou do Tratamento, não tem o que mostrar)
-- ==========================================
CREATE VIEW vw_copia_vigente AS
SELECT DISTINCT ON (ap.processo_id)
       ap.processo_id,
       ap.id     AS arquivo_processado_id,
       ap.versao
FROM arquivo_processado ap
WHERE EXISTS (SELECT 1 FROM feicao_carga fc WHERE fc.arquivo_processado_id = ap.id)
ORDER BY ap.processo_id, ap.versao DESC, ap.id DESC;

COMMENT ON VIEW vw_copia_vigente IS
    'Cópia de arquivo_processado que vale para cada processo: maior versão entre as que têm feições extraídas. Regra única usada pelo mapa (V23) e pelas APIs das Tarefas 13 e 14.';

-- ==========================================
-- Recria o mapa da V23 usando a cópia vigente. Muda só o CTE "publicado";
-- o resto (colunas, índices, grant) é igual ao da V23.
-- A fn_dados_publicados_geojson é LANGUAGE sql com corpo em texto, então não depende
-- da view no catálogo e continua funcionando depois do DROP/CREATE.
-- ==========================================
DROP MATERIALIZED VIEW vw_dados_publicados_mapa;

CREATE MATERIALIZED VIEW vw_dados_publicados_mapa AS
WITH publicado AS (
    SELECT cv.processo_id,
           cv.arquivo_processado_id
    FROM vw_copia_vigente cv
    JOIN processo p ON p.id = cv.processo_id
    WHERE p.versao_fechada
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
    'Geometrias finais de cargas publicadas (versao_fechada = TRUE), na cópia de vw_copia_vigente, com hectares sobrepostos e metadados de safra. Atualizada sozinha pela trigger tg_atualiza_mapa_ao_publicar.';
COMMENT ON COLUMN vw_dados_publicados_mapa.hectares_sobrepostos_total IS
    'Soma de resultados_calculos.hectares_sobrepostos do imóvel (todas as camadas de referência cruzadas).';

CREATE UNIQUE INDEX uq_dpm_feicao ON vw_dados_publicados_mapa(feicao_id);
CREATE INDEX idx_dpm_geom ON vw_dados_publicados_mapa USING GIST (geom);
CREATE INDEX idx_dpm_conjunto_safra_orgao ON vw_dados_publicados_mapa(conjunto_id, ano_safra, orgao_id);
CREATE INDEX idx_dpm_orgao                ON vw_dados_publicados_mapa(orgao_id);
CREATE INDEX idx_dpm_processo             ON vw_dados_publicados_mapa(processo_id);
CREATE INDEX idx_dpm_versao_oficial       ON vw_dados_publicados_mapa(versao_oficial);

-- o DROP leva o grant junto, então devolve (a role continua sem ver a vw_copia_vigente)
GRANT SELECT ON vw_dados_publicados_mapa TO analista_mapa;

-- ==========================================
-- Refresh automático quando a versão fecha.
-- Pela V22 a versão só fecha uma vez (FALSE -> TRUE) e depois disso processo, cópia,
-- feições e cálculos ficam travados, então esse é o único momento em que o mapa muda.
-- Roda na mesma transação do fechamento: se o refresh falhar, o fechamento também volta,
-- e nunca fica carga publicada fora do mapa.
-- SECURITY DEFINER porque o REFRESH exige ser dono da view e a
-- fn_atualizar_dados_publicados() está sem EXECUTE pro PUBLIC (V23).
-- ==========================================
CREATE OR REPLACE FUNCTION fn_atualiza_mapa_ao_publicar()
RETURNS TRIGGER
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    PERFORM fn_atualizar_dados_publicados();
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

REVOKE EXECUTE ON FUNCTION fn_atualiza_mapa_ao_publicar() FROM PUBLIC;

CREATE TRIGGER tg_atualiza_mapa_ao_publicar
    AFTER UPDATE OF versao_fechada ON processo
    FOR EACH ROW
    WHEN (NEW.versao_fechada AND NOT OLD.versao_fechada)
    EXECUTE FUNCTION fn_atualiza_mapa_ao_publicar();
