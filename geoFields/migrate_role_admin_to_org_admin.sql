-- =============================================================================
-- Роль в приложении: ORG_ADMIN (не ADMIN). Старый CHECK на users.role без
-- ORG_ADMIN даёт: «нарушает ограничение-проверку users_role_check».
--
-- Выполните этот скрипт целиком на своей базе (psql, DBeaver, pgAdmin).
-- Безопасно запускать повторно.
-- =============================================================================

-- Старые строки с ролью ADMIN → ORG_ADMIN (если ещё остались после смены кода).
UPDATE users SET role = 'ORG_ADMIN' WHERE role = 'ADMIN';

-- Снимаем CHECK на role: стандартное имя в PostgreSQL — users_role_check;
-- на всякий случай снимаем любой CHECK у таблицы users, в определении которого есть "role".
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conname::text AS cn
        FROM pg_constraint c
        WHERE c.conrelid = to_regclass('users')
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%role%'
    LOOP
        EXECUTE format('ALTER TABLE users DROP CONSTRAINT IF EXISTS %I', r.cn);
    END LOOP;
END $$;

ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'AGRONOMIST', 'ORG_MANAGER', 'ORG_ADMIN'));

COMMENT ON COLUMN users.role IS 'USER | AGRONOMIST | ORG_MANAGER | ORG_ADMIN';
