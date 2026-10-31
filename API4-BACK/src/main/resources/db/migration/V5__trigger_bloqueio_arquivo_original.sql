-- Tarefa 2 (Travas de Segurança) - Sprint 1 / RN02
-- Bloqueia PERMANENTEMENTE qualquer UPDATE ou DELETE em arquivo_original,
-- direto no banco - não depende do ORM nem de disciplina da aplicação.
-- Funciona mesmo que alguém rode um UPDATE manual via psql/DBeaver.

CREATE OR REPLACE FUNCTION fn_bloquear_alteracao_arquivo_original()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'RN02: a tabela arquivo_original é imutável. Operação % não é permitida (id do registro: %).',
        TG_OP,
        OLD.id;
    RETURN NULL; -- nunca alcançado, mas exigido pela assinatura da função
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_bloquear_update_arquivo_original
    BEFORE UPDATE ON arquivo_original
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloquear_alteracao_arquivo_original();

CREATE TRIGGER trg_bloquear_delete_arquivo_original
    BEFORE DELETE ON arquivo_original
    FOR EACH ROW
    EXECUTE FUNCTION fn_bloquear_alteracao_arquivo_original();

-- Opcional (defesa em camadas, se o app usar uma ROLE de banco própria
-- e não o superuser): revogar os privilégios também a nível de permissão.
-- Só faça isso se souberem exatamente qual ROLE a aplicação usa, senão
-- pode travar o próprio INSERT sem querer.
-- REVOKE UPDATE, DELETE ON arquivo_original FROM app_user;
