-- Демо-операции для полей: 26/422 (id 45), 24/434 (id 44), 23/325 (id 46), организация 1.

DELETE FROM field_operations WHERE field_id IN (44, 45, 46);

INSERT INTO field_operations (field_id, organization_id, user_id, operation_at, name, category, status) VALUES
-- 26/422 озимая пшеница (field_id 45)
(45, 1, 4, '2025-08-12 09:00:00', 'Вспашка зяби', 'TILLAGE', 'COMPLETED'),
(45, 1, 4, '2025-08-28 10:30:00', 'Внесение фосфорно-калийных удобрений', 'FERTILIZATION', 'COMPLETED'),
(45, 1, 4, '2025-09-05 07:15:00', 'Посев озимой пшеницы', 'SOWING', 'COMPLETED'),
(45, 1, 4, '2025-09-06 14:00:00', 'Боронование после посева', 'TILLAGE', 'COMPLETED'),
(45, 1, 4, '2025-10-01 11:00:00', 'Осмотр всходов', 'SCOUTING', 'COMPLETED'),
(45, 1, 4, '2026-04-10 08:00:00', 'Подкормка азотом (весна)', 'FERTILIZATION', 'IN_PROGRESS'),
(45, 1, 4, '2026-04-18 06:30:00', 'Опрыскивание от сорняков', 'PEST_CONTROL', 'STARTED'),
(45, 1, 4, '2026-05-20 12:00:00', 'Полив при недостатке влаги', 'IRRIGATION', 'PLANNED'),
(45, 1, 4, '2026-07-15 05:00:00', 'Уборка урожая (план)', 'HARVEST', 'PLANNED'),
(45, 1, 4, '2025-11-20 13:00:00', 'Отбор проб почвы NPK', 'OTHER', 'COMPLETED'),
-- 24/434 озимая пшеница семена (field_id 44)
(44, 1, 4, '2025-08-20 09:30:00', 'Глубокое рыхление', 'TILLAGE', 'COMPLETED'),
(44, 1, 4, '2025-09-01 08:00:00', 'Калибровка сеялки', 'OTHER', 'COMPLETED'),
(44, 1, 4, '2025-09-08 07:00:00', 'Посев семенного участка озимой пшеницы', 'SOWING', 'COMPLETED'),
(44, 1, 4, '2025-09-12 15:00:00', 'Прикатывание', 'TILLAGE', 'COMPLETED'),
(44, 1, 4, '2025-10-05 10:00:00', 'Контроль густоты всходов', 'SCOUTING', 'COMPLETED'),
(44, 1, 4, '2026-03-25 09:00:00', 'Обработка от болезней', 'PEST_CONTROL', 'IN_PROGRESS'),
(44, 1, 4, '2026-04-05 11:30:00', 'Внесение микроудобрений', 'FERTILIZATION', 'STARTED'),
(44, 1, 4, '2026-06-01 08:00:00', 'Роговая обмолотка (контроль)', 'SCOUTING', 'PLANNED'),
(44, 1, 4, '2026-07-20 06:00:00', 'Уборка семенного урожая', 'HARVEST', 'PLANNED'),
(44, 1, 4, '2025-12-01 14:00:00', 'Сушка и очистка семян', 'OTHER', 'FROZEN'),
-- 23/325 пар (field_id 46)
(46, 1, 4, '2025-06-10 08:00:00', 'Дискование пара', 'TILLAGE', 'COMPLETED'),
(46, 1, 4, '2025-07-05 09:00:00', 'Культивация', 'TILLAGE', 'COMPLETED'),
(46, 1, 4, '2025-08-01 10:00:00', 'Лущение сорняков', 'TILLAGE', 'COMPLETED'),
(46, 1, 4, '2025-09-15 11:00:00', 'Осмотр чистоты пара', 'SCOUTING', 'COMPLETED'),
(46, 1, 4, '2026-04-01 08:30:00', 'Боронование весной', 'TILLAGE', 'COMPLETED'),
(46, 1, 4, '2026-04-20 07:00:00', 'Внесение аммиачной селитры перед посевом', 'FERTILIZATION', 'IN_PROGRESS'),
(46, 1, 4, '2026-05-10 12:00:00', 'Плановый посев (след. культура)', 'SOWING', 'PLANNED'),
(46, 1, 4, '2025-10-20 13:00:00', 'Известкование (план)', 'FERTILIZATION', 'PLANNED'),
(46, 1, 4, '2025-11-10 09:00:00', 'Контроль эрозии', 'SCOUTING', 'STARTED'),
(46, 1, 4, '2025-12-15 10:00:00', 'Снегозадержание', 'OTHER', 'CANCELLED');

-- Журнал статусов: текущий статус на дату операции
INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id, note)
SELECT fo.id, fo.status, fo.operation_at, fo.user_id, NULL
FROM field_operations fo
WHERE fo.field_id IN (44, 45, 46);

-- Дополнительные переходы для завершённых операций
INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id)
SELECT fo.id, 'PLANNED', fo.operation_at - INTERVAL '10 days', fo.user_id
FROM field_operations fo
WHERE fo.field_id IN (44, 45, 46) AND fo.status = 'COMPLETED';

INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id)
SELECT fo.id, 'STARTED', fo.operation_at - INTERVAL '4 days', fo.user_id
FROM field_operations fo
WHERE fo.field_id IN (44, 45, 46) AND fo.status = 'COMPLETED';

INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id)
SELECT fo.id, 'IN_PROGRESS', fo.operation_at - INTERVAL '1 day', fo.user_id
FROM field_operations fo
WHERE fo.field_id IN (44, 45, 46) AND fo.status = 'COMPLETED';

-- Для операций в работе
INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id)
SELECT fo.id, 'PLANNED', fo.operation_at - INTERVAL '5 days', fo.user_id
FROM field_operations fo
WHERE fo.field_id IN (44, 45, 46) AND fo.status IN ('IN_PROGRESS', 'STARTED');

INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id)
SELECT fo.id, 'STARTED', fo.operation_at - INTERVAL '2 days', fo.user_id
FROM field_operations fo
WHERE fo.field_id IN (44, 45, 46) AND fo.status = 'IN_PROGRESS';
