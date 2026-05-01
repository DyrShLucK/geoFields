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

-- 3. Пользователи (organization_id подтягивается динамически)
INSERT INTO users (login, email, password_hash, organization_id)
SELECT 'ivanov', 'ivanov@agro.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id
FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"'
UNION ALL
SELECT 'petrov', 'petrov@agro.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id
FROM organizations WHERE name = 'ООО "АгроХолдинг Юг"'
UNION ALL
SELECT 'sidorov', 'sidorov@zeldolina.ru', '$2b$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LewY5GyYuJ0u.5jIy', id
FROM organizations WHERE name = 'КФХ "Зеленая Долина"';

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

-- 5. NDVI данные (ссылки)
INSERT INTO ndvi_data (url) VALUES
                                ('https://earthengine.../tiles/{z}/{x}/{y}?token=abc1'),
                                ('https://earthengine.../tiles/{z}/{x}/{y}?token=abc2'),
                                ('https://earthengine.../tiles/{z}/{x}/{y}?token=abc3'),
                                ('https://earthengine.../tiles/{z}/{x}/{y}?token=abc4'),
                                ('https://earthengine.../tiles/{z}/{x}/{y}?token=abc5');

-- 6. Аналитика (field_crop_id и ndvi_id подтягиваются автоматически)
INSERT INTO field_analytics (field_crop_id, ndvi_id, record_date)
VALUES
-- Для пшеницы (field_id = 1)
((SELECT history_id FROM field_crops WHERE field_id = 1 LIMIT 1), 1, '2026-04-01'),
((SELECT history_id FROM field_crops WHERE field_id = 1 LIMIT 1), 2, '2026-04-15'),
((SELECT history_id FROM field_crops WHERE field_id = 1 LIMIT 1), 3, '2026-04-25'),
-- Для кукурузы (field_id = 2)
((SELECT history_id FROM field_crops WHERE field_id = 2 LIMIT 1), 4, '2026-04-10'),
((SELECT history_id FROM field_crops WHERE field_id = 2 LIMIT 1), 5, '2026-04-20');