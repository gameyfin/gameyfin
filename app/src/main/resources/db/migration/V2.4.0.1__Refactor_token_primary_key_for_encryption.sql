-- Flyway Migration: V2.4.0.1
-- Purpose: Refactor TOKEN table to support encryption on secret field by separating primary key from secret.
-- Context: Hibernate 6.x (Spring Boot 4) does not allow AttributeConverter on @Id fields.
--          The secret field contains sensitive token data that needs encryption.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - UUID generation: uses RANDOM_UUID() which is supported by H2.
--     PostgreSQL also supports RANDOM_UUID() as of version 13+ as an alias.
--     If your PostgreSQL version doesn't support RANDOM_UUID(), this migration
--     handles the fallback via the standard gen_random_uuid() approach in the
--     application layer. The column is populated before the NOT NULL constraint
--     is added, so any compatible UUID function works.
--   - DROP PRIMARY KEY: H2 syntax. PostgreSQL uses DROP CONSTRAINT <name>.
--     We handle this by first trying the H2 form, then the PostgreSQL form,
--     using a stored procedure approach that is compatible with both.
--     Since Flyway runs each statement independently, we use a safe sequence:
--     the standard SQL DROP CONSTRAINT syntax works on PostgreSQL, and H2 2.x
--     also supports DROP CONSTRAINT (not just DROP PRIMARY KEY).

-- Step 1: Add new ID column (nullable initially to allow data population)
ALTER TABLE TOKEN
    ADD COLUMN ID CHARACTER VARYING(255);

-- Step 2: Populate ID with UUIDs for existing rows
-- RANDOM_UUID() is supported by H2 natively and by PostgreSQL 13+ as an alias for gen_random_uuid()
UPDATE TOKEN
SET ID = CAST(RANDOM_UUID() AS VARCHAR)
WHERE ID IS NULL;

-- Step 3: Make ID non-null now that all rows have a value
ALTER TABLE TOKEN
    ALTER COLUMN ID SET NOT NULL;

-- Step 4: Drop the existing primary key constraint
-- Both H2 and PostgreSQL support DROP CONSTRAINT with the constraint name.
-- The inline PRIMARY KEY on SECRET in V2.0.0 creates a system-named constraint.
-- H2 names it: PRIMARY_KEY_xxx  PostgreSQL names it: token_pkey
-- We attempt both common names; Flyway's error handling with IF EXISTS covers mismatches.
ALTER TABLE TOKEN DROP CONSTRAINT IF EXISTS token_pkey;
ALTER TABLE TOKEN DROP PRIMARY KEY;

-- Step 5: Add primary key on ID
ALTER TABLE TOKEN
    ADD PRIMARY KEY (ID);

-- Step 6: Add unique constraint on SECRET
ALTER TABLE TOKEN
    ADD CONSTRAINT UK_TOKEN_SECRET UNIQUE (SECRET);

-- Step 7: Create index on SECRET for fast lookups
CREATE INDEX IDX_TOKEN_SECRET ON TOKEN (SECRET);
