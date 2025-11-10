-- Ensure pgcrypto for gen_random_uuid
CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
BEGIN
    -- Only migrate if partners.id is not already UUID
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'partners' AND column_name = 'id' AND data_type <> 'uuid'
    ) THEN
        -- New UUID columns
        ALTER TABLE partners ADD COLUMN IF NOT EXISTS id2 uuid DEFAULT gen_random_uuid() NOT NULL;
        ALTER TABLE analysis_records ADD COLUMN IF NOT EXISTS partner_id2 uuid NULL;

        -- Populate partner_id2 by joining old text id to partners.id
        UPDATE analysis_records ar
        SET partner_id2 = p.id2
        FROM partners p
        WHERE ar.partner_id IS NOT NULL AND p.id = ar.partner_id;

        -- Drop old FK if exists
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_name = 'analysis_records' AND constraint_name = 'fk_analysis_partner'
        ) THEN
            ALTER TABLE analysis_records DROP CONSTRAINT fk_analysis_partner;
        END IF;

        -- Drop old PK if exists to allow dropping column
        IF EXISTS (
            SELECT 1 FROM information_schema.table_constraints
            WHERE table_name = 'partners' AND constraint_type = 'PRIMARY KEY'
        ) THEN
            ALTER TABLE partners DROP CONSTRAINT IF EXISTS partners_pkey;
        END IF;

        -- Switch columns
        ALTER TABLE partners DROP COLUMN id;
        ALTER TABLE partners RENAME COLUMN id2 TO id;

        ALTER TABLE analysis_records DROP COLUMN partner_id;
        ALTER TABLE analysis_records RENAME COLUMN partner_id2 TO partner_id;

        -- Recreate PK and FK
        ALTER TABLE partners ADD PRIMARY KEY (id);
        ALTER TABLE analysis_records
            ADD CONSTRAINT fk_analysis_partner
            FOREIGN KEY (partner_id) REFERENCES partners(id)
            ON UPDATE CASCADE ON DELETE SET NULL;

        -- Recreate index
        CREATE INDEX IF NOT EXISTS idx_analysis_records_partner_id ON analysis_records(partner_id);
    END IF;
END$$;

