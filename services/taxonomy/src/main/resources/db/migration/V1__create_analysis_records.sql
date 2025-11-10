-- Initial schema: create analysis_records table

-- Enable pgcrypto extension for UUID generation
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS analysis_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    filename VARCHAR(500) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    top_category_label VARCHAR(255),
    top_category_confidence DOUBLE PRECISION,
    category_json JSONB,
    attributes_json JSONB,
    colors_json JSONB,
    raw_json JSONB,
    error_message TEXT
);

-- Create index for common queries
CREATE INDEX IF NOT EXISTS idx_analysis_records_status ON analysis_records(status);
CREATE INDEX IF NOT EXISTS idx_analysis_records_created_at ON analysis_records(created_at DESC);

