-- Flyway Migration: V2.3.0.4
-- Purpose: Rename config key from 'library.allow-public-access' to 'security.allow-public-access'
-- Applies only if the old key has been set by the user

-- Update key name if present
UPDATE APP_CONFIG
SET "key" = 'security.allow-public-access'
WHERE "key" = 'library.allow-public-access';

