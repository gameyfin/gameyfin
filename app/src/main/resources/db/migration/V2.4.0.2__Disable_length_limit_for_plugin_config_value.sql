-- Flyway Migration: V2.4.0.2
-- Purpose: Make PLUGIN_CONFIG."value" column unbounded to avoid length errors when storing large values.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - CLOB does not exist in PostgreSQL. The equivalent is TEXT (unlimited length).
--   - ALTER TABLE ... ALTER COLUMN ... TYPE TEXT is valid PostgreSQL syntax.

ALTER TABLE PLUGIN_CONFIG
    ALTER COLUMN "value" TYPE TEXT;
