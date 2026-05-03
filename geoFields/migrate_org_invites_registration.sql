-- Реферальные ссылки + статус регистрации (PENDING до одобрения менеджером организации).
-- Выполнить на существующей БД PostgreSQL (иначе вход падает: столбец registration_status не существует).
--
-- Пример:
--   psql -h localhost -U postgres -d geofields -f migrate_org_invites_registration.sql
-- В psql уже подключившись: \i migrate_org_invites_registration.sql

ALTER TABLE users ADD COLUMN IF NOT EXISTS registration_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';

UPDATE users SET registration_status = 'APPROVED' WHERE registration_status IS NULL OR registration_status = '';

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_registration_status_check;
ALTER TABLE users ADD CONSTRAINT users_registration_status_check
    CHECK (registration_status IN ('PENDING', 'APPROVED', 'REJECTED'));

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'ORG_ADMIN', 'ORG_MANAGER'));

CREATE TABLE IF NOT EXISTS org_registration_invites (
    id              SERIAL PRIMARY KEY,
    organization_id INTEGER NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    token           VARCHAR(64) NOT NULL UNIQUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER REFERENCES users (id) ON DELETE SET NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_org_invites_org ON org_registration_invites (organization_id);
CREATE INDEX IF NOT EXISTS idx_org_invites_token ON org_registration_invites (token) WHERE NOT revoked;

ALTER TABLE org_registration_invites ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMP NULL;
COMMENT ON COLUMN org_registration_invites.consumed_at IS 'Одноразовая ссылка: после успешной регистрации';

DROP INDEX IF EXISTS idx_org_invites_token;
CREATE INDEX idx_org_invites_token ON org_registration_invites (token) WHERE NOT revoked AND consumed_at IS NULL;

COMMENT ON TABLE org_registration_invites IS 'Реферальные токены: регистрация только по ?ref=token (один раз)';
COMMENT ON COLUMN users.registration_status IS 'PENDING — ждёт менеджера; APPROVED — может войти; REJECTED — отклонён';
