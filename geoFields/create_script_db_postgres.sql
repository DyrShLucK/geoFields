-- ============================================
-- ПОЛНЫЙ СКРИПТ СОЗДАНИЯ БАЗЫ ДАННЫХ
-- ============================================

-- ============================================
-- УДАЛЕНИЕ ТАБЛИЦ (если существуют)
-- ============================================
DROP TABLE IF EXISTS field_analytics CASCADE;
DROP TABLE IF EXISTS ndvi_data CASCADE;
DROP TABLE IF EXISTS field_crops CASCADE;
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
-- 3. ПОЛЯ
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
                       login VARCHAR(100) NOT NULL,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       is_active BOOLEAN DEFAULT TRUE,
                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                       organization_id INTEGER NOT NULL,

                       CONSTRAINT fk_users_organization
                           FOREIGN KEY (organization_id)
                               REFERENCES organizations(id)
                               ON DELETE CASCADE
);

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
                             sown_area_ha NUMERIC(10,2),
                             harvested_area_ha NUMERIC(10,2),
                             actual_yield NUMERIC(8,2),
                             total_yield NUMERIC(10,2),
                             planned_yield NUMERIC(8,2),
                             forecasted_yield NUMERIC(8,2),
                             source_data VARCHAR(50),
                             sowing_details VARCHAR(100),
                             crop_year INTEGER,

                             CONSTRAINT fk_field_crops_crop
                                 FOREIGN KEY (crop_id)
                                     REFERENCES crops(crop_id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_field_crops_field
                                 FOREIGN KEY (field_id)
                                     REFERENCES fields(id)
                                     ON DELETE CASCADE,

                             CONSTRAINT fk_field_crops_organization
                                 FOREIGN KEY (organization_id)
                                     REFERENCES organizations(id)
                                     ON DELETE SET NULL
);

-- ============================================
-- 6. NDVI ДАННЫЕ (ссылки на снимки)
-- ============================================
CREATE TABLE ndvi_data (
                           id SERIAL PRIMARY KEY,
                           url TEXT,
                           created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 7. АНАЛИТИКА ПОЛЕЙ (с привязкой к NDVI)
-- ============================================
CREATE TABLE field_analytics (
                                 id SERIAL PRIMARY KEY,
                                 field_crop_id INTEGER NOT NULL,
                                 ndvi_id INTEGER,
                                 record_date DATE NOT NULL,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

                                 CONSTRAINT fk_analytics_field_crop
                                     FOREIGN KEY (field_crop_id)
                                         REFERENCES field_crops(history_id)
                                         ON DELETE CASCADE,

                                 CONSTRAINT fk_analytics_ndvi
                                     FOREIGN KEY (ndvi_id)
                                         REFERENCES ndvi_data(id)
                                         ON DELETE SET NULL
);

-- ============================================
-- ИНДЕКСЫ
-- ============================================
CREATE INDEX idx_users_organization_id ON users(organization_id);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_field_crops_crop_id ON field_crops(crop_id);
CREATE INDEX idx_field_crops_field_id ON field_crops(field_id);
CREATE INDEX idx_field_crops_organization_id ON field_crops(organization_id);
CREATE INDEX idx_field_crops_crop_year ON field_crops(crop_year);
CREATE INDEX idx_analytics_field_crop_id ON field_analytics(field_crop_id);
CREATE INDEX idx_analytics_ndvi_id ON field_analytics(ndvi_id);
CREATE INDEX idx_analytics_record_date ON field_analytics(record_date);

-- ============================================
-- КОММЕНТАРИИ К ТАБЛИЦАМ И КОЛОНКАМ
-- ============================================
COMMENT ON TABLE organizations IS 'Справочник организаций/хозяйств';
COMMENT ON TABLE crops IS 'Справочник сельскохозяйственных культур';
COMMENT ON TABLE fields IS 'Геоданные полей (границы, площадь)';
COMMENT ON TABLE users IS 'Пользователи системы';
COMMENT ON TABLE field_crops IS 'История посевов: какая культура на каком поле и когда';
COMMENT ON TABLE ndvi_data IS 'Ссылки на NDVI снимки (тайлы GEE или статические изображения)';
COMMENT ON TABLE field_analytics IS 'Аналитические данные по полям с привязкой к NDVI';

COMMENT ON COLUMN users.password_hash IS 'Хэш пароля (bcrypt/argon2)';
COMMENT ON COLUMN users.is_active IS 'Статус активности пользователя';
COMMENT ON COLUMN fields.geom IS 'Геометрия поля в формате MultiPolygon (SRID: 4326)';
COMMENT ON COLUMN field_crops.sown_area_ha IS 'Посевная площадь (гектары)';
COMMENT ON COLUMN field_crops.harvested_area_ha IS 'Убранная площадь (гектары)';
COMMENT ON COLUMN field_crops.actual_yield IS 'Фактическая урожайность (ц/га)';
COMMENT ON COLUMN field_analytics.ndvi_id IS 'Ссылка на NDVI данные (может быть NULL)';
COMMENT ON COLUMN ndvi_data.url IS 'URL тайлового сервера GEE или прямой ссылки на GeoTIFF/PNG';

-- ============================================
-- ПРИМЕР ЗАПОЛНЕНИЯ (опционально)
-- ============================================
-- INSERT INTO organizations (name) VALUES ('ООО АгроХолдинг');
-- INSERT INTO crops (crop_name) VALUES ('Пшеница'), ('Кукуруза'), ('Подсолнечник');
-- INSERT INTO users (login, email, password_hash, organization_id)
-- VALUES ('admin', 'admin@agro.ru', '$2b$12$...', 1);