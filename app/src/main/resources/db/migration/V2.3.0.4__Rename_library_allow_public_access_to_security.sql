-- Flyway Migration: V2.3.0.4
-- Purpose: Rename config key from 'library.allow-public-access' to 'security.allow-public-access'.
-- Compatibility: H2 2.2+ and PostgreSQL 13+ Fully compatible. Standard UPDATE syntax.

UPDATE APP_CONFIG
SET "key" = 'security.allow-public-access'
WHERE "key" = 'library.allow-public-access';
