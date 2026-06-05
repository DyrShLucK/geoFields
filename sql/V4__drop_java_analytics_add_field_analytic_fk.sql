-- Удаляем Java-схему NDVI (field_analytics + ndvi_data). Кэш NDVI остаётся в field_analytic (Python, V2).

DROP TABLE IF EXISTS field_analytics CASCADE;
DROP TABLE IF EXISTS ndvi_data CASCADE;

-- FK field_analytic.field_id → fields(id)
DELETE FROM field_analytic fa
WHERE NOT EXISTS (SELECT 1 FROM fields f WHERE f.id = fa.field_id);

ALTER TABLE field_analytic
    DROP CONSTRAINT IF EXISTS fk_field_analytic_field;

ALTER TABLE field_analytic
    ADD CONSTRAINT fk_field_analytic_field
        FOREIGN KEY (field_id)
            REFERENCES fields (id)
            ON DELETE CASCADE;

COMMENT ON CONSTRAINT fk_field_analytic_field ON field_analytic IS
    'Операции NDVI/геоаналитики Python привязаны к полю';
