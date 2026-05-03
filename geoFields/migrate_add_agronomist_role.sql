-- Роль AGRONOMIST в users.role (PostgreSQL: пересоздать CHECK).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'AGRONOMIST', 'ORG_MANAGER', 'ORG_ADMIN'));

COMMENT ON COLUMN users.role IS 'USER | AGRONOMIST | ORG_MANAGER | ORG_ADMIN';
