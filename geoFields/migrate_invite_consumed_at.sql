-- Одноразовые приглашения: колонка consumed_at (если миграция org_invites уже была без неё).
ALTER TABLE org_registration_invites ADD COLUMN IF NOT EXISTS consumed_at TIMESTAMP NULL;

DROP INDEX IF EXISTS idx_org_invites_token;
CREATE INDEX idx_org_invites_token ON org_registration_invites (token) WHERE NOT revoked AND consumed_at IS NULL;
