-- ============================================
-- GeoFields: создание схемы PostgreSQL + PostGIS
-- Актуально под текущее приложение (Spring + JDBC).
-- Порядок: psql -f create_script_db_postgres.sql, затем при необходимости test_data_db.sql
-- ============================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================
-- УДАЛЕНИЕ (порядок с учётом внешних ключей)
-- ============================================
DROP TABLE IF EXISTS field_analytics CASCADE;
DROP TABLE IF EXISTS ndvi_data CASCADE;
DROP TABLE IF EXISTS field_crops CASCADE;
DROP TABLE IF EXISTS org_registration_invites CASCADE;
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS fields CASCADE;
DROP TABLE IF EXISTS crops CASCADE;
DROP TABLE IF EXISTS organizations CASCADE;

-- ============================================
-- 1. ОРГАНИЗАЦИИ
-- ============================================
CREATE TABLE organizations (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 2. СПРАВОЧНИК КУЛЬТУР
-- ============================================
CREATE TABLE crops (
    crop_id SERIAL PRIMARY KEY,
    crop_name VARCHAR(100) NOT NULL
);

-- ============================================
-- 3. ПОЛЯ (геометрия MultiPolygon, SRID 4326)
-- ============================================
CREATE TABLE fields (
    id SERIAL PRIMARY KEY,
    geom GEOMETRY(MultiPolygon, 4326),
    field_id NUMERIC,
    field_name VARCHAR(254),
    field_area NUMERIC
);

-- ============================================
-- 4. ПОЛЬЗОВАТЕЛИ
-- ============================================
CREATE TABLE users (
    id SERIAL PRIMARY KEY,
    login VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    organization_id INTEGER NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'USER'
        CHECK (role IN ('USER', 'AGRONOMIST', 'ORG_MANAGER', 'ORG_ADMIN')),
    registration_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
        CHECK (registration_status IN ('PENDING', 'APPROVED', 'REJECTED')),
    last_name VARCHAR(100) NOT NULL DEFAULT '',
    first_name VARCHAR(100) NOT NULL DEFAULT '',
    middle_name VARCHAR(100) NOT NULL DEFAULT '',

    CONSTRAINT fk_users_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations (id)
            ON DELETE CASCADE
);

-- ============================================
-- 4.1 ПРИГЛАШЕНИЯ НА РЕГИСТРАЦИЮ
-- ============================================
CREATE TABLE org_registration_invites (
    id SERIAL PRIMARY KEY,
    organization_id INTEGER NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    token VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by_user_id INTEGER REFERENCES users (id) ON DELETE SET NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMP NULL
);

CREATE INDEX idx_org_invites_org ON org_registration_invites (organization_id);
CREATE INDEX idx_org_invites_token ON org_registration_invites (token) WHERE NOT revoked AND consumed_at IS NULL;

-- ============================================
-- 5. ИСТОРИЯ ПОСЕВОВ (FIELD_CROPS)
-- ============================================
CREATE TABLE field_crops (
    history_id SERIAL PRIMARY KEY,
    crop_id INTEGER NOT NULL,
    field_id INTEGER NOT NULL,
    organization_id INTEGER,
    sowing_date DATE,
    harvest_date DATE,
    sown_area_ha NUMERIC(10, 2),
    harvested_area_ha NUMERIC(10, 2),
    actual_yield NUMERIC(8, 2),
    total_yield NUMERIC(10, 2),
    planned_yield NUMERIC(8, 2),
    forecasted_yield NUMERIC(8, 2),
    source_data VARCHAR(50),
    sowing_details VARCHAR(100),
    crop_year INTEGER,

    CONSTRAINT fk_field_crops_crop
        FOREIGN KEY (crop_id)
            REFERENCES crops (crop_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_crops_field
        FOREIGN KEY (field_id)
            REFERENCES fields (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_crops_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations (id)
            ON DELETE SET NULL
);

-- ============================================
-- 6. NDVI (ссылки на тайлы / растры)
-- ============================================
CREATE TABLE ndvi_data (
    id SERIAL PRIMARY KEY,
    url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 7. АНАЛИТИКА ПОЛЕЙ (привязка к строке field_crops и NDVI)
-- ============================================
CREATE TABLE field_analytics (
    id SERIAL PRIMARY KEY,
    field_crop_id INTEGER NOT NULL,
    ndvi_id INTEGER,
    record_date DATE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_analytics_field_crop
        FOREIGN KEY (field_crop_id)
            REFERENCES field_crops (history_id)
            ON DELETE CASCADE,

    CONSTRAINT fk_analytics_ndvi
        FOREIGN KEY (ndvi_id)
            REFERENCES ndvi_data (id)
            ON DELETE SET NULL
);

-- ============================================
-- ИНДЕКСЫ
-- ============================================
CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_field_crops_crop_id ON field_crops (crop_id);
CREATE INDEX idx_field_crops_field_id ON field_crops (field_id);
CREATE INDEX idx_field_crops_organization_id ON field_crops (organization_id);
CREATE INDEX idx_field_crops_crop_year ON field_crops (crop_year);
CREATE INDEX idx_analytics_field_crop_id ON field_analytics (field_crop_id);
CREATE INDEX idx_analytics_ndvi_id ON field_analytics (ndvi_id);
CREATE INDEX idx_analytics_record_date ON field_analytics (record_date);
CREATE INDEX idx_fields_geom ON fields USING GIST (geom);

-- ============================================
-- КОММЕНТАРИИ
-- ============================================
COMMENT ON TABLE organizations IS 'Справочник организаций/хозяйств';
COMMENT ON TABLE crops IS 'Справочник сельскохозяйственных культур';
COMMENT ON TABLE fields IS 'Геоданные полей (границы, площадь)';
COMMENT ON TABLE users IS 'Пользователи системы';
COMMENT ON TABLE org_registration_invites IS 'Одноразовые токены регистрации в организацию';
COMMENT ON TABLE field_crops IS 'История посевов: культура, поле, организация, урожайность';
COMMENT ON TABLE ndvi_data IS 'Ссылки на NDVI (тайлы и т.п.)';
COMMENT ON TABLE field_analytics IS 'Записи аналитики по строке field_crops, опционально с NDVI';

COMMENT ON COLUMN users.password_hash IS 'Хэш пароля (bcrypt и т.п.)';
COMMENT ON COLUMN users.is_active IS 'Аккаунт активен';
COMMENT ON COLUMN fields.geom IS 'MultiPolygon, SRID 4326 (WGS 84)';
COMMENT ON COLUMN field_crops.sown_area_ha IS 'Посевная площадь, га';
COMMENT ON COLUMN field_crops.harvested_area_ha IS 'Убранная площадь, га';
COMMENT ON COLUMN field_crops.actual_yield IS 'Фактическая урожайность (как в вашей методике)';
COMMENT ON COLUMN field_analytics.field_crop_id IS 'FK на field_crops.history_id';
COMMENT ON COLUMN field_analytics.ndvi_id IS 'Ссылка на ndvi_data (может быть NULL)';
COMMENT ON COLUMN ndvi_data.url IS 'URL тайлового слоя или файла';
