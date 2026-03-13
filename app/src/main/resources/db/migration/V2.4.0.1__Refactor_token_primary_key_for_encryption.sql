-- Flyway Migration: V2.4.0.1
-- Purpose: Refactor TOKEN table to support encryption on secret field by separating primary key from secret.
-- Context: Hibernate 6.x (Spring Boot 4) does not allow AttributeConverter on @Id fields.
--          The secret field contains sensitive token data (password reset tokens, etc.) that needs encryption.
-- Strategy:
--   Modify the existing TOKEN table in-place by adding a new ID column and restructuring constraints.

-- Step 1: Add new ID column (nullable initially to allow data population)
ALTER TABLE TOKEN
    ADD COLUMN ID CHARACTER VARYING(255);

-- Step 2: Populate ID column with new UUIDs for existing rows
UPDATE TOKEN
SET ID = RANDOM_UUID()
WHERE ID IS NULL;

-- Step 3: Make ID column non-null now that it has values
ALTER TABLE TOKEN
    ALTER COLUMN ID SET NOT NULL;

-- Step 4: Drop the primary key constraint on SECRET
-- H2 uses auto-generated constraint names, so we need to find and drop it
-- The primary key constraint is typically named PRIMARY_KEY_XXX or CONSTRAINT_XXX
ALTER TABLE TOKEN
    DROP PRIMARY KEY;

-- Step 5: Add primary key constraint on ID
ALTER TABLE TOKEN
    ADD PRIMARY KEY (ID);

-- Step 6: Add unique constraint on SECRET (it was previously the primary key, so it was already unique)
-- The SECRET column should remain unique for lookups
ALTER TABLE TOKEN
    ADD CONSTRAINT UK_TOKEN_SECRET UNIQUE (SECRET);

-- Step 7: Create index on SECRET for fast lookups
CREATE INDEX IDX_TOKEN_SECRET ON TOKEN (SECRET);
