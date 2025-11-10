-- Add partners table and partner_id column to analysis_records

CREATE TABLE IF NOT EXISTS partners (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);

ALTER TABLE analysis_records
    ADD COLUMN IF NOT EXISTS partner_id VARCHAR(100);

-- Optional index for filtering by partner
CREATE INDEX IF NOT EXISTS idx_analysis_records_partner_id ON analysis_records(partner_id);

-- Add FK, tolerate if it already exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_analysis_partner'
          AND table_name = 'analysis_records'
    ) THEN
        ALTER TABLE analysis_records
            ADD CONSTRAINT fk_analysis_partner
            FOREIGN KEY (partner_id)
            REFERENCES partners(id)
            ON UPDATE CASCADE
            ON DELETE SET NULL;
    END IF;
END$$;

