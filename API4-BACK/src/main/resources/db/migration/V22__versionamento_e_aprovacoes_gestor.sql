-- V22: versionamento/congelamento de cargas + histórico de aprovações do gestor
-- Tarefas 19, 20, 21 e 22.
--
-- "Carga" = processo. "Geometrias tratadas" = feicao_carga (V21).
-- "Resultados de cálculo" = resultados_calculos (V20).

-- ==========================================
-- TAREFA 20: colunas de controle de versão e congelamento
-- ==========================================
ALTER TABLE processo
    ADD COLUMN versao_oficial        VARCHAR(20),
    ADD COLUMN versao_fechada        BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_publicacao       TIMESTAMP,
    ADD COLUMN gestor_publicacao_id  BIGINT REFERENCES usuario(id);

-- Uma versão só pode ser fechada com os dados oficiais preenchidos.
ALTER TABLE processo
    ADD CONSTRAINT ck_processo_versao_fechada_completa CHECK (
        versao_fechada = FALSE
        OR (versao_oficial IS NOT NULL
            AND data_publicacao IS NOT NULL
            AND gestor_publicacao_id IS NOT NULL)
    );

CREATE INDEX idx_processo_gestor_publicacao ON processo(gestor_publicacao_id);

-- ==========================================
-- TAREFA 19: histórico de aprovações do gestor (gates)
-- ==========================================
CREATE TABLE historico_aprovacoes_gestor (
    id                      BIGSERIAL PRIMARY KEY,
    processo_id             BIGINT NOT NULL REFERENCES processo(id),
    gestor_id               BIGINT NOT NULL REFERENCES usuario(id),
    etapa_avaliada          VARCHAR(20) NOT NULL,
    decisao                 VARCHAR(20) NOT NULL,
    parecer_justificativa   TEXT,
    data_decisao            TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT ck_hist_aprov_etapa
        CHECK (etapa_avaliada IN ('VALIDACAO', 'TRATAMENTO', 'PUBLICACAO')),
    CONSTRAINT ck_hist_aprov_decisao
        CHECK (decisao IN ('APROVADO', 'REPROVADO')),
    -- Reprovação exige justificativa.
    CONSTRAINT ck_hist_aprov_parecer_reprovacao
        CHECK (decisao = 'APROVADO'
               OR (parecer_justificativa IS NOT NULL AND btrim(parecer_justificativa) <> ''))
);

CREATE INDEX idx_hist_aprov_processo_etapa ON historico_aprovacoes_gestor(processo_id, etapa_avaliada);
CREATE INDEX idx_hist_aprov_gestor         ON historico_aprovacoes_gestor(gestor_id);

COMMENT ON TABLE historico_aprovacoes_gestor IS
    'Histórico imutável das decisões do gestor em cada gate (Validação, Tratamento, Publicação) de uma carga.';

-- Histórico de auditoria é append-only (mesmo padrão da V5).
CREATE OR REPLACE FUNCTION fn_bloquear_alteracao_historico_aprovacoes()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'historico_aprovacoes_gestor é imutável. Operação % não é permitida (id do registro: %).',
        TG_OP, OLD.id;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bloquear_alteracao_historico_aprovacoes
    BEFORE UPDATE OR DELETE ON historico_aprovacoes_gestor
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloquear_alteracao_historico_aprovacoes();

-- ==========================================
-- TAREFA 22: aprovador precisa existir (FK) E estar ativo (trigger)
-- FK sozinha não valida "ativo". A checagem é só na gravação do aprovador:
-- desativar um gestor depois não pode travar o histórico nem o processo.
-- ==========================================
CREATE OR REPLACE FUNCTION fn_exige_usuario_ativo(p_usuario_id BIGINT, p_contexto TEXT)
RETURNS VOID AS $$
DECLARE
    v_ativo BOOLEAN;
BEGIN
    SELECT ativo INTO v_ativo FROM usuario WHERE id = p_usuario_id;

    IF v_ativo IS DISTINCT FROM TRUE THEN
        RAISE EXCEPTION 'Aprovador inválido: usuário % não existe ou está inativo (%).',
            p_usuario_id, p_contexto;
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_valida_gestor_historico()
RETURNS TRIGGER AS $$
BEGIN
    PERFORM fn_exige_usuario_ativo(NEW.gestor_id, 'historico_aprovacoes_gestor.gestor_id');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_valida_gestor_historico
    BEFORE INSERT ON historico_aprovacoes_gestor
    FOR EACH ROW
    EXECUTE FUNCTION fn_valida_gestor_historico();

CREATE OR REPLACE FUNCTION fn_valida_gestor_publicacao()
RETURNS TRIGGER AS $$
BEGIN
    -- Só valida quando o gestor é informado/alterado; updates comuns do
    -- processo não podem falhar porque o gestor foi desativado depois.
    IF NEW.gestor_publicacao_id IS NOT NULL
       AND (TG_OP = 'INSERT' OR NEW.gestor_publicacao_id IS DISTINCT FROM OLD.gestor_publicacao_id) THEN
        PERFORM fn_exige_usuario_ativo(NEW.gestor_publicacao_id, 'processo.gestor_publicacao_id');
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_valida_gestor_publicacao
    BEFORE INSERT OR UPDATE OF gestor_publicacao_id ON processo
    FOR EACH ROW
    EXECUTE FUNCTION fn_valida_gestor_publicacao();

-- ==========================================
-- TAREFA 21: bloqueio de versão fechada (UPDATE/DELETE)
-- ==========================================

-- 1) processo: depois de fechado, nada muda. Fechar (FALSE -> TRUE) é permitido,
--    desde que o último parecer de PUBLICACAO seja APROVADO.
--    Reabrir (TRUE -> FALSE) cai no bloqueio, pois OLD.versao_fechada = TRUE.
CREATE OR REPLACE FUNCTION fn_bloqueia_versao_fechada_processo()
RETURNS TRIGGER AS $$
DECLARE
    v_ultima_decisao VARCHAR(20);
BEGIN
    IF OLD.versao_fechada THEN
        RAISE EXCEPTION
            'Versão fechada: o processo % é imutável. Operação % abortada em processo.',
            OLD.id, TG_OP;
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.versao_fechada THEN
        SELECT h.decisao INTO v_ultima_decisao
        FROM historico_aprovacoes_gestor h
        WHERE h.processo_id = OLD.id
          AND h.etapa_avaliada = 'PUBLICACAO'
        ORDER BY h.data_decisao DESC, h.id DESC
        LIMIT 1;

        IF v_ultima_decisao IS DISTINCT FROM 'APROVADO' THEN
            RAISE EXCEPTION
                'Não é possível fechar a versão do processo %: falta aprovação do gestor na etapa PUBLICACAO.',
                OLD.id;
        END IF;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_bloqueia_versao_fechada_processo
    BEFORE UPDATE OR DELETE ON processo
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloqueia_versao_fechada_processo();

-- 2) Tabelas filhas. FOR SHARE segura a linha do processo e evita que um
--    fechamento concorrente passe entre a checagem e o UPDATE/DELETE.
CREATE OR REPLACE FUNCTION fn_processo_esta_fechado(p_processo_id BIGINT)
RETURNS BOOLEAN AS $$
DECLARE
    v_fechada BOOLEAN;
BEGIN
    SELECT p.versao_fechada INTO v_fechada
    FROM processo p
    WHERE p.id = p_processo_id
    FOR SHARE;

    RETURN COALESCE(v_fechada, FALSE);
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION fn_bloqueia_versao_fechada_filha()
RETURNS TRIGGER AS $$
DECLARE
    v_processo_id BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'feicao_carga' THEN
        -- feicao_carga não tem processo_id: sobe via arquivo_processado (V21).
        SELECT ap.processo_id INTO v_processo_id
        FROM arquivo_processado ap
        WHERE ap.id = OLD.arquivo_processado_id;
    ELSE
        v_processo_id := OLD.processo_id;
    END IF;

    IF fn_processo_esta_fechado(v_processo_id) THEN
        RAISE EXCEPTION
            'Versão fechada: o processo % é imutável. Operação % abortada em %.',
            v_processo_id, TG_OP, TG_TABLE_NAME;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_bloqueia_versao_fechada_resultados
    BEFORE UPDATE OR DELETE ON resultados_calculos
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloqueia_versao_fechada_filha();

CREATE TRIGGER tg_bloqueia_versao_fechada_feicao
    BEFORE UPDATE OR DELETE ON feicao_carga
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloqueia_versao_fechada_filha();

-- A V14 só trava arquivo_processado durante a quarentena; sem esta trigger
-- ele ficaria editável numa versão já publicada.
CREATE TRIGGER tg_bloqueia_versao_fechada_arquivo_processado
    BEFORE UPDATE OR DELETE ON arquivo_processado
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloqueia_versao_fechada_filha();