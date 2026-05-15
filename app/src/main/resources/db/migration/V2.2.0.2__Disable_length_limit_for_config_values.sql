-- Flyway Migration: V2.2.0.2
-- Purpose: Make APP_CONFIG."value" column unbounded to avoid length errors when storing large encrypted values.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - CLOB does not exist in PostgreSQL. The equivalent is TEXT (unlimited length).
--   - ALTER TABLE ... ALTER COLUMN ... TYPE is valid PostgreSQL syntax.

ALTER TABLE APP_CONFIG
    ALTER COLUMN "value" TYPE TEXT;
