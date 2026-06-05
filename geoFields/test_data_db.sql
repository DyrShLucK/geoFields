-- ============================================
-- ТЕСТОВЫЕ ДАННЫЕ (ID генерируются автоматически)
-- ============================================

-- 1. Организации
INSERT INTO organizations (name) VALUES
                                     ('ООО "АгроХолдинг Юг"'),
                                     ('КФХ "Зеленая Долина"');

-- 2. Культуры
INSERT INTO crops (crop_name) VALUES
                                  ('Пшеница озимая'),
                                  ('Кукуруза на зерно'),
                                  ('Подсолнечник'),
                                  ('Ячмень яровой'),
                                  ('Соя'),
                                  ('Рапс озимый');

-- 3. Пользователи (organization_id подтягивается динамически; пароль один и тот же тестовый хеш bcrypt)
INSERT INTO users (login, email, password_hash, organization_id, role, registration_status, last_name, first_name, middle_name)
SELECT 'ivanov', 'ivanov@agro.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id, 'ORG_ADMIN', 'APPROVED', 'Иванов', 'Иван', 'Иванович'
FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"'
UNION ALL
SELECT 'petrov', 'petrov@agro.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id, 'ORG_MANAGER', 'APPROVED', 'Петров', 'Пётр', 'Петрович'
FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"'
UNION ALL
SELECT 'sidorov', 'sidorov@zeldolina.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id, 'USER', 'APPROVED', 'Сидоров', 'Сидор', 'Сидорович'
FROM organizations WHERE name = 'КФХ "Зеленая Долина"'
UNION ALL
SELECT 'agronom1', 'agronom1@agro.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id, 'AGRONOMIST', 'APPROVED', 'Смирнов', 'Алексей', 'Николаевич'
FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"';

-- Демо-приглашение (одноразовое): после одной успешной регистрации токен сгорает
-- /register?ref=demo-invite-agro-2026
INSERT INTO org_registration_invites (organization_id, token, revoked)
SELECT id, 'demo-invite-agro-2026', false
FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"';

-- 4. История посевов (2 записи для полей 1 и 2)
INSERT INTO field_crops (
    crop_id, field_id, organization_id, sowing_date, harvest_date,
    sown_area_ha, harvested_area_ha, actual_yield, total_yield,
    planned_yield, forecasted_yield, source_data, sowing_details, crop_year
) VALUES
      (
          (SELECT crop_id FROM crops WHERE crop_name = 'Пшеница озимая'),
          1,
          (SELECT id FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"'),
          '2025-09-15', '2026-07-20', 125.50, 123.00, 45.50, 5596.50,
          42.00, 44.00, 'Агроном', 'Сорт Московская 39', 2026
      ),
      (
          (SELECT crop_id FROM crops WHERE crop_name = 'Кукуруза на зерно'),
          2,
          (SELECT id FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"'),
          '2026-04-25', '2026-10-15', 87.30, NULL, NULL, NULL,
          65.00, 68.50, 'Агроном', 'Гибрид Краснодарский 436МВ', 2026
      );

-- NDVI-кэш: таблица field_analytic (Flyway V2), заполняется Python-сервисом при расчёте снимков.