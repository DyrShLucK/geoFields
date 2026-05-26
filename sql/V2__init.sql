CREATE TABLE scenes (
    id SERIAL PRIMARY KEY,
    scene_id TEXT UNIQUE NOT NULL,
    platform TEXT,
    acquisition_date DATE,
    cloud_cover FLOAT,
    footprint GEOMETRY,
    epsg INTEGER,
    local_path TEXT,
    is_downloaded BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE field_scenes (
    id SERIAL PRIMARY KEY,
    field_id INTEGER,
    scene_id INTEGER,
    intersect_area FLOAT,
    processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE scene_indices (
    id SERIAL PRIMARY KEY,
    scene_id INTEGER REFERENCES scenes(id) ON DELETE CASCADE,
    index_type VARCHAR(10) NOT NULL,
    local_path TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(scene_id, index_type)
);

CREATE TABLE field_analytic (
    id SERIAL PRIMARY KEY,
    field_id INTEGER NOT NULL,
    scene_index_id INTEGER REFERENCES scene_indices(id),
    date DATE NOT NULL,
    local_path TEXT NOT NULL,
    mean_value FLOAT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_field_res ON field_analytic(field_id, date);