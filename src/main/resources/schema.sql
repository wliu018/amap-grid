-- PostgreSQL 14+；不需要 PostGIS。手动执行，启动不会自动重建表。
CREATE TABLE IF NOT EXISTS amap_grid_probe (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(32) NOT NULL,
    region VARCHAR(20) NOT NULL,
    poi_type VARCHAR(100) NOT NULL,
    grid_size_meter INTEGER NOT NULL CHECK (grid_size_meter > 0),
    min_lng NUMERIC(12,8) NOT NULL,
    min_lat NUMERIC(12,8) NOT NULL,
    max_lng NUMERIC(12,8) NOT NULL,
    max_lat NUMERIC(12,8) NOT NULL,
    center_lng NUMERIC(12,8) NOT NULL,
    center_lat NUMERIC(12,8) NOT NULL,
    poi_count INTEGER CHECK (poi_count >= 0),
    probe_level INTEGER NOT NULL,
    probe_status VARCHAR(20) NOT NULL CHECK (probe_status IN ('SUCCESS','FAILED')),
    error_message VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_amap_grid_probe_region_type ON amap_grid_probe(region, poi_type);
CREATE INDEX IF NOT EXISTS idx_amap_grid_probe_batch ON amap_grid_probe(batch_no);
CREATE INDEX IF NOT EXISTS idx_amap_grid_probe_level ON amap_grid_probe(batch_no, probe_level);

CREATE TABLE IF NOT EXISTS amap_grid_config (
    id BIGSERIAL PRIMARY KEY,
    region VARCHAR(20) NOT NULL,
    poi_type VARCHAR(100) NOT NULL,
    recommended_grid_size INTEGER NOT NULL,
    safe_count INTEGER NOT NULL,
    max_probe_count INTEGER,
    current_batch_no VARCHAR(32),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_amap_grid_config_region_type UNIQUE(region, poi_type)
);

CREATE TABLE IF NOT EXISTS amap_grid_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(32) NOT NULL,
    region VARCHAR(20) NOT NULL,
    poi_type VARCHAR(100) NOT NULL,
    recommended_grid_size INTEGER,
    safe_count INTEGER,
    max_probe_count INTEGER,
    grid_count INTEGER DEFAULT 0,
    inside_grid_count INTEGER DEFAULT 0,
    boundary_grid_count INTEGER DEFAULT 0,
    status VARCHAR(20) NOT NULL CHECK (status IN ('CREATING','SUCCESS','FAILED')),
    error_message VARCHAR(1000),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finish_time TIMESTAMP,
    CONSTRAINT uk_amap_grid_batch_no UNIQUE(batch_no)
);

CREATE TABLE IF NOT EXISTS amap_grid (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(32) NOT NULL,
    region VARCHAR(20) NOT NULL,
    poi_type VARCHAR(100) NOT NULL,
    grid_no VARCHAR(140) NOT NULL,
    row_index INTEGER NOT NULL,
    col_index INTEGER NOT NULL,
    min_lng NUMERIC(12,8) NOT NULL,
    min_lat NUMERIC(12,8) NOT NULL,
    max_lng NUMERIC(12,8) NOT NULL,
    max_lat NUMERIC(12,8) NOT NULL,
    center_lng NUMERIC(12,8) NOT NULL,
    center_lat NUMERIC(12,8) NOT NULL,
    grid_size_meter INTEGER NOT NULL,
    relation_type SMALLINT NOT NULL CHECK (relation_type IN (1,2)),
    random_order BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' CHECK (status IN ('WAITING','RUNNING','SUCCESS','FAILED')),
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_amap_grid_batch_grid UNIQUE(batch_no, grid_no)
);
CREATE INDEX IF NOT EXISTS idx_amap_grid_task ON amap_grid(batch_no, status, random_order);
CREATE INDEX IF NOT EXISTS idx_amap_grid_region_type ON amap_grid(region, poi_type);
CREATE INDEX IF NOT EXISTS idx_amap_grid_position ON amap_grid(batch_no, row_index, col_index);
-- grid_no 长度为 140：支持 types 最长100字符以及行列编号，不截断稳定编号。
