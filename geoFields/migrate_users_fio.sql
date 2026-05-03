-- ФИО пользователя: три колонки (фамилия, имя, отчество).
ALTER TABLE users ADD COLUMN IF NOT EXISTS last_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS first_name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE users ADD COLUMN IF NOT EXISTS middle_name VARCHAR(100) NOT NULL DEFAULT '';

COMMENT ON COLUMN users.last_name IS 'Фамилия';
COMMENT ON COLUMN users.first_name IS 'Имя';
COMMENT ON COLUMN users.middle_name IS 'Отчество';
