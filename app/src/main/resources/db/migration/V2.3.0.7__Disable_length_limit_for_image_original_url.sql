-- Flyway Migration: V2.2.0.7
-- Purpose: Make IMAGE.ORIGINAL_URL column unbounded to avoid length errors when storing large encrypted values.
-- Context: Previously defined as CHARACTER VARYING(255); H2 raised 22001 (value too long).
-- Strategy: Alter column type to CLOB (unlimited length in H2). This matches other large text usages (e.g., COMMENT, SUMMARY) which use CHARACTER LARGE OBJECT.

ALTER TABLE IMAGE
    ALTER COLUMN ORIGINAL_URL CLOB;

