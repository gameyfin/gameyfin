-- Flyway Migration: V2.3.0.7
-- Purpose: Make IMAGE.ORIGINAL_URL column unbounded to avoid length errors when storing large encrypted values.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - CLOB does not exist in PostgreSQL. The equivalent is TEXT (unlimited length).
--   - ALTER TABLE ... ALTER COLUMN ... TYPE TEXT is valid PostgreSQL syntax.
--   - USING clause is added to handle the implicit cast from VARCHAR(255) to TEXT.

ALTER TABLE IMAGE
    ALTER COLUMN ORIGINAL_URL TYPE TEXT;
