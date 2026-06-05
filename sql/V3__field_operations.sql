-- Операции по полю (агротехнические мероприятия): удобрения, посев, уборка и т.д.
-- Статусы и категории — справочники (вместо PostgreSQL ENUM для удобства расширения и подписей в UI).

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

CREATE INDEX idx_field_operations_field_id ON field_operations (field_id);
CREATE INDEX idx_field_operations_org_id ON field_operations (organization_id);
CREATE INDEX idx_field_operations_operation_at ON field_operations (operation_at DESC);
CREATE INDEX idx_field_operations_status ON field_operations (status);
CREATE INDEX idx_field_operations_category ON field_operations (category);
CREATE INDEX idx_field_operations_field_org_at
    ON field_operations (field_id, organization_id, operation_at DESC);

COMMENT ON TABLE field_operation_status IS 'Справочник статусов операции по полю';
COMMENT ON TABLE field_operation_category IS 'Справочник категорий операции (тип мероприятия)';
COMMENT ON TABLE field_operations IS 'Операции по полю: дата/время, название, категория, статус, автор';
COMMENT ON COLUMN field_operations.operation_at IS 'Дата и время выполнения (плановая или фактическая)';
COMMENT ON COLUMN field_operations.name IS 'Наименование операции, напр. «Внесение NPK 120 кг/га»';
COMMENT ON COLUMN field_operations.user_id IS 'Пользователь, создавший или зафиксировавший запись';
