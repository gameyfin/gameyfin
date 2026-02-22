-- Flyway Migration: V2.4.0.2
-- Purpose: Make PLUGIN_CONFIG.value column unbounded to avoid length errors when storing large values.
-- Context: Previously defined as CHARACTER VARYING(255); H2 raised 22001 (value too long).
-- Strategy: Alter column type to CLOB (unlimited length in H2). This matches other large text usages (e.g., COMMENT, SUMMARY) which use CHARACTER LARGE OBJECT.

ALTER TABLE PLUGIN_CONFIG
    ALTER COLUMN "value" CLOB;