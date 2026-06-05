-- История смены статусов операции по полю (когда начато, завершено и т.д.).

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

CREATE INDEX idx_field_op_status_hist_operation
    ON field_operation_status_history (operation_id, changed_at ASC);

COMMENT ON TABLE field_operation_status_history IS 'Журнал переходов статуса операции по полю';
COMMENT ON COLUMN field_operation_status_history.note IS 'Комментарий агронома при смене статуса (необязательно)';

-- Начальная запись в истории для уже существующих операций
INSERT INTO field_operation_status_history (operation_id, status, changed_at, user_id)
SELECT fo.id, fo.status, fo.created_at, fo.user_id
FROM field_operations fo
WHERE NOT EXISTS (
    SELECT 1 FROM field_operation_status_history h WHERE h.operation_id = fo.id
);
