-- Связь «поле ↔ сцена»: FK на fields и scenes (только SQL, Python не трогаем).

DELETE FROM field_scenes fs
WHERE fs.field_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM fields f WHERE f.id = fs.field_id);

DELETE FROM field_scenes fs
WHERE fs.scene_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM scenes s WHERE s.id = fs.scene_id);

ALTER TABLE field_scenes
    DROP CONSTRAINT IF EXISTS fk_field_scenes_field;

ALTER TABLE field_scenes
    DROP CONSTRAINT IF EXISTS fk_field_scenes_scene;

ALTER TABLE field_scenes
    ADD CONSTRAINT fk_field_scenes_field
        FOREIGN KEY (field_id)
            REFERENCES fields (id)
            ON DELETE CASCADE;

ALTER TABLE field_scenes
    ADD CONSTRAINT fk_field_scenes_scene
        FOREIGN KEY (scene_id)
            REFERENCES scenes (id)
            ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_field_scenes_field_id ON field_scenes (field_id);
CREATE INDEX IF NOT EXISTS idx_field_scenes_scene_id ON field_scenes (scene_id);

COMMENT ON TABLE field_scenes IS 'Какие спутниковые сцены пересекают контур поля (для индексации NDVI)';
COMMENT ON CONSTRAINT fk_field_scenes_field ON field_scenes IS 'Поле из учётной схемы GeoFields (fields.id)';
COMMENT ON CONSTRAINT fk_field_scenes_scene ON field_scenes IS 'Сцена из каталога scenes (scenes.id)';
