-- ============================================
-- GeoFields: создание схемы PostgreSQL + PostGIS
-- Актуально под текущее приложение (Spring + JDBC).
-- Порядок: psql -f create_script_db_postgres.sql, затем при необходимости test_data_db.sql
-- ============================================

CREATE EXTENSION IF NOT EXISTS postgis;

-- ============================================
-- УДАЛЕНИЕ (порядок с учётом внешних ключей)
-- ============================================
DROP TABLE IF EXISTS field_operation_status_history CASCADE;
DROP TABLE IF EXISTS field_operations CASCADE;
DROP TABLE IF EXISTS field_operation_category CASCADE;
DROP TABLE IF EXISTS field_operation_status CASCADE;
DROP TABLE IF EXISTS field_analytic CASCADE;
DROP TABLE IF EXISTS scene_indices CASCADE;
DROP TABLE IF EXISTS field_scenes CASCADE;
DROP TABLE IF EXISTS scenes CASCADE;
DROP TABLE IF EXISTS field_crops CASCADE;
DROP TABLE IF EXISTS field_intersections CASCADE;
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
    field_area NUMERIC,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============================================
-- 3.1 ПЕРЕСЕЧЕНИЯ ПОЛЕЙ
-- ============================================
CREATE TABLE field_intersections (
    organization_id INTEGER NOT NULL,
    field_id_left INTEGER NOT NULL,
    field_id_right INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_field_intersections
        PRIMARY KEY (field_id_left, field_id_right),

    CONSTRAINT chk_field_intersections_order
        CHECK (field_id_left < field_id_right),

    CONSTRAINT fk_field_intersections_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_intersections_left
        FOREIGN KEY (field_id_left)
            REFERENCES fields (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_intersections_right
        FOREIGN KEY (field_id_right)
            REFERENCES fields (id)
            ON DELETE CASCADE
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
-- 5.1 ОПЕРАЦИИ ПО ПОЛЮ (агротехнические мероприятия)
-- ============================================
CREATE TABLE field_operation_status (
    code        VARCHAR(32) PRIMARY KEY,
    title_ru    VARCHAR(100) NOT NULL,
    sort_order  SMALLINT NOT NULL DEFAULT 0
);

INSERT INTO field_operation_status (code, title_ru, sort_order) VALUES
    ('PLANNED',     'Запланировано', 10),
    ('STARTED',     'Начато',        20),
    ('IN_PROGRESS', 'В процессе',    30),
    ('COMPLETED',   'Завершено',     40),
    ('FROZEN',      'Заморожено',    50),
    ('CANCELLED',   'Отменено',      60);

CREATE TABLE field_operation_category (
    code        VARCHAR(50) PRIMARY KEY,
    title_ru    VARCHAR(100) NOT NULL,
    sort_order  SMALLINT NOT NULL DEFAULT 0
);

INSERT INTO field_operation_category (code, title_ru, sort_order) VALUES
    ('FERTILIZATION', 'Внесение удобрений', 10),
    ('SOWING',        'Посев',              20),
    ('HARVEST',       'Уборка урожая',      30),
    ('TILLAGE',       'Обработка почвы',    40),
    ('IRRIGATION',    'Полив / орошение',   50),
    ('PEST_CONTROL',  'Защита растений',    60),
    ('SCOUTING',      'Осмотр поля',        70),
    ('OTHER',         'Прочее',             99);

CREATE TABLE field_operations (
    id              SERIAL PRIMARY KEY,
    field_id        INTEGER NOT NULL,
    organization_id INTEGER NOT NULL,
    user_id         INTEGER,
    operation_at    TIMESTAMP NOT NULL,
    name            VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    status          VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_field_operations_field
        FOREIGN KEY (field_id)
            REFERENCES fields (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_operations_organization
        FOREIGN KEY (organization_id)
            REFERENCES organizations (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_operations_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE SET NULL,

    CONSTRAINT fk_field_operations_category
        FOREIGN KEY (category)
            REFERENCES field_operation_category (code),

    CONSTRAINT fk_field_operations_status
        FOREIGN KEY (status)
            REFERENCES field_operation_status (code)
);

CREATE TABLE field_operation_status_history (
    id              SERIAL PRIMARY KEY,
    operation_id    INTEGER NOT NULL,
    status          VARCHAR(32) NOT NULL,
    changed_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id         INTEGER,
    note            VARCHAR(500),

    CONSTRAINT fk_field_op_status_hist_operation
        FOREIGN KEY (operation_id)
            REFERENCES field_operations (id)
            ON DELETE CASCADE,

    CONSTRAINT fk_field_op_status_hist_status
        FOREIGN KEY (status)
            REFERENCES field_operation_status (code),

    CONSTRAINT fk_field_op_status_hist_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
            ON DELETE SET NULL
);

-- NDVI/сцены: таблицы scenes, field_analytic и др. создаются миграциями Flyway (sql/V2+, V4+).

-- ============================================
-- ИНДЕКСЫ
-- ============================================
CREATE INDEX idx_users_organization_id ON users (organization_id);
CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_field_crops_crop_id ON field_crops (crop_id);
CREATE INDEX idx_field_crops_field_id ON field_crops (field_id);
CREATE INDEX idx_field_crops_organization_id ON field_crops (organization_id);
CREATE INDEX idx_field_crops_crop_year ON field_crops (crop_year);
CREATE INDEX idx_field_operations_field_id ON field_operations (field_id);
CREATE INDEX idx_field_operations_org_id ON field_operations (organization_id);
CREATE INDEX idx_field_operations_operation_at ON field_operations (operation_at DESC);
CREATE INDEX idx_field_operations_status ON field_operations (status);
CREATE INDEX idx_field_operations_category ON field_operations (category);
CREATE INDEX idx_field_operations_field_org_at
    ON field_operations (field_id, organization_id, operation_at DESC);
CREATE INDEX idx_field_op_status_hist_operation
    ON field_operation_status_history (operation_id, changed_at ASC);
CREATE INDEX idx_field_intersections_org ON field_intersections (organization_id);
CREATE INDEX idx_field_intersections_left ON field_intersections (field_id_left);
CREATE INDEX idx_field_intersections_right ON field_intersections (field_id_right);
CREATE INDEX idx_fields_geom ON fields USING GIST (geom);

-- ============================================
-- КОММЕНТАРИИ
-- ============================================
COMMENT ON TABLE organizations IS 'Справочник организаций/хозяйств';
COMMENT ON TABLE crops IS 'Справочник сельскохозяйственных культур';
COMMENT ON TABLE fields IS 'Геоданные полей (границы, площадь)';
COMMENT ON TABLE field_intersections IS 'Пары полей одной организации, чьи контуры пересекаются';
COMMENT ON TABLE users IS 'Пользователи системы';
COMMENT ON TABLE org_registration_invites IS 'Одноразовые токены регистрации в организацию';
COMMENT ON TABLE field_crops IS 'История посевов: культура, поле, организация, урожайность';
COMMENT ON TABLE field_operation_status IS 'Справочник статусов операции по полю';
COMMENT ON TABLE field_operation_category IS 'Справочник категорий операции (тип мероприятия)';
COMMENT ON TABLE field_operations IS 'Операции по полю: дата/время, название, категория, статус, автор';
COMMENT ON COLUMN field_operations.operation_at IS 'Дата и время выполнения (плановая или фактическая)';
COMMENT ON COLUMN field_operations.name IS 'Наименование операции, напр. «Внесение NPK 120 кг/га»';
COMMENT ON COLUMN field_operations.user_id IS 'Пользователь, создавший или зафиксировавший запись';
COMMENT ON TABLE field_operation_status_history IS 'Журнал переходов статуса операции по полю';
COMMENT ON COLUMN users.password_hash IS 'Хэш пароля (bcrypt и т.п.)';
COMMENT ON COLUMN users.is_active IS 'Аккаунт активен';
COMMENT ON COLUMN fields.geom IS 'MultiPolygon, SRID 4326 (WGS 84)';
COMMENT ON COLUMN fields.is_active IS 'TRUE - активное поле, FALSE - устаревшее и скрыто из выдачи';
COMMENT ON COLUMN field_intersections.field_id_left IS 'Меньший id поля в паре пересечения';
COMMENT ON COLUMN field_intersections.field_id_right IS 'Больший id поля в паре пересечения';
COMMENT ON COLUMN field_crops.sown_area_ha IS 'Посевная площадь, га';
COMMENT ON COLUMN field_crops.harvested_area_ha IS 'Убранная площадь, га';
COMMENT ON COLUMN field_crops.actual_yield IS 'Фактическая урожайность (как в вашей методике)';
