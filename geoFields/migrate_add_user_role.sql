-- Выполнить на существующей БД, если таблица users создана без role / без UNIQUE(login).
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(32) NOT NULL DEFAULT 'USER';
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ORG_MANAGER', 'ORG_ADMIN'));

CREATE UNIQUE INDEX IF NOT EXISTS users_login_key ON users (login);
