-- Flyway Migration: V2.3.0.6
-- Purpose: Add blurhash support to images for improved UI performance.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - Removed H2-specific CREATE ALIAS / CALL / DROP ALIAS blocks entirely.
--     PostgreSQL has no CREATE ALIAS mechanism and cannot call Java methods from SQL.
--   - The blurhash calculation for existing images CANNOT be performed in SQL on
--     PostgreSQL. This must be handled at the application layer.
--   - Recommended approach: After this migration runs, implement a one-time data
--     migration in the application startup (e.g., a Spring ApplicationRunner or
--     CommandLineRunner) that reads each IMAGE row, computes the blurhash in Java,
--     and writes it back. The BLURHASH column is nullable so existing rows without
--     a blurhash will simply return null until populated.

ALTER TABLE IMAGE ADD COLUMN BLURHASH VARCHAR(255);
