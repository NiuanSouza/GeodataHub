-- V14: trava RN03 - bloqueia UPDATE/DELETE em arquivo_processado enquanto
-- o processo pai estiver em quarentena (situacao = EM_VALIDACAO).
--
-- Escopo: RN03 fala em "dado original ou derivado". O dado original
-- (arquivo_original) já é imutável PERMANENTEMENTE pela trigger da V5
-- (RN02) - mais restritivo que RN03, então já está coberto. Esta trigger
-- cobre o "dado derivado" (arquivo_processado), bloqueando só enquanto
-- durar a quarentena (ao contrário da V5, que bloqueia pra sempre).
--
-- Trigger em vez de RLS: RLS depende de contexto de sessão (quem é o
-- usuário/perfil da conexão) pra decidir a policy, mas a app conecta no
-- Postgres com um único usuário (application.properties: root) — sem
-- role por perfil, RLS não tem o que checar. Trigger valida direto pelo
-- estado do dado (situacao_atual_id do processo), funciona independente
-- de quem/como a conexão foi aberta (inclusive psql/DBeaver manual).

CREATE OR REPLACE FUNCTION fn_bloquear_edicao_quarentena()
RETURNS TRIGGER AS $$
DECLARE
    v_situacao_nome VARCHAR(50);
BEGIN
    SELECT s.nome INTO v_situacao_nome
    FROM processo p
    JOIN situacao s ON s.id = p.situacao_atual_id
    WHERE p.id = OLD.processo_id;

    IF v_situacao_nome = 'EM_VALIDACAO' THEN
        RAISE EXCEPTION
            'RN03: processo % está em quarentena (EM_VALIDACAO) - operação % não é permitida em arquivo_processado (id do registro: %).',
            OLD.processo_id, TG_OP, OLD.id;
    END IF;

    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bloquear_edicao_arquivo_processado
    BEFORE UPDATE OR DELETE ON arquivo_processado
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloquear_edicao_quarentena();