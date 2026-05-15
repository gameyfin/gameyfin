-- Flyway Migration: V2.3.0.1
-- Purpose: Convert config values from old format to JSON format for consistency.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   Uses only standard SQL expressions compatible with both databases.
--   H2 Java aliases (toJsonString, toJsonBoolean, toJsonInt, toJsonArray) are replaced
--   with pure SQL equivalents that work identically on both databases:
--
--   toJsonString(v)  → CONCAT('"', REPLACE(v, '"', '\"'), '"')
--                      wraps the value in JSON double-quotes
--
--   toJsonBoolean(v) → CASE WHEN LOWER(v) IN ('true','1','yes') THEN 'true' ELSE 'false' END
--
--   toJsonInt(v)     → CAST(CAST(v AS BIGINT) AS VARCHAR)
--                      validates the value is numeric and normalises it
--
--   toJsonArray(v)   → Cannot be done in pure SQL compatibly across H2 and PostgreSQL.
--                      Instead we use a simple string transformation that wraps the
--                      comma-separated value in a JSON array format using CONCAT.
--                      This handles the common case of simple string arrays.

-- Convert String values to JSON format (wrap in double-quotes)
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'logs.folder';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'library.scan.title-extraction-regex';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'library.metadata.update.schedule';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'messages.providers.email.host';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'messages.providers.email.username';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'messages.providers.email.password';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.roles-claim';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.client-id';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.client-secret';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.issuer-url';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.authorize-url';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.token-url';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.userinfo-url';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.jwks-url';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.logout-url';

-- Convert Boolean values to JSON format (true/false without quotes)
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'library.allow-public-access';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'library.scan.enable-filesystem-watcher';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'library.scan.scan-empty-directories';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'library.scan.extract-title-using-regex';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'library.metadata.update.enabled';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'requests.games.enabled';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'requests.games.allow-guests-to-request-games';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'downloads.bandwidth-limit.enabled';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'users.sign-ups.allow';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'users.sign-ups.confirmation-required';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'sso.oidc.enabled';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'sso.oidc.auto-register-new-users';
UPDATE APP_CONFIG SET "value" = CASE WHEN LOWER("value") IN ('true','1','yes') THEN 'true' ELSE 'false' END WHERE "key" = 'messages.providers.email.enabled';

-- Convert Int values to JSON format (plain numbers)
UPDATE APP_CONFIG SET "value" = CAST(CAST("value" AS BIGINT) AS VARCHAR) WHERE "key" = 'logs.max-history-days';
UPDATE APP_CONFIG SET "value" = CAST(CAST("value" AS BIGINT) AS VARCHAR) WHERE "key" = 'library.scan.title-match-min-ratio';
UPDATE APP_CONFIG SET "value" = CAST(CAST("value" AS BIGINT) AS VARCHAR) WHERE "key" = 'requests.games.max-open-requests-per-user';
UPDATE APP_CONFIG SET "value" = CAST(CAST("value" AS BIGINT) AS VARCHAR) WHERE "key" = 'downloads.bandwidth-limit.mbps';
UPDATE APP_CONFIG SET "value" = CAST(CAST("value" AS BIGINT) AS VARCHAR) WHERE "key" = 'messages.providers.email.port';

-- Convert Enum values to JSON format (quoted strings)
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'logs.level.gameyfin';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'logs.level.root';
UPDATE APP_CONFIG SET "value" = CONCAT('"', REPLACE("value", '"', '\"'), '"') WHERE "key" = 'sso.oidc.match-existing-users-by';

-- Convert Array values to JSON format (comma-separated → JSON array)
-- Wraps each comma-separated element in quotes and brackets.
-- Works for simple string arrays (no embedded commas or quotes in values).
UPDATE APP_CONFIG
SET "value" = CONCAT('[', REPLACE(CONCAT('"', REPLACE("value", ',', '","'), '"'), '""', ''), ']')
WHERE "key" = 'library.scan.game-file-extensions'
  AND "value" IS NOT NULL AND "value" <> '';

UPDATE APP_CONFIG
SET "value" = CONCAT('[', REPLACE(CONCAT('"', REPLACE("value", ',', '","'), '"'), '""', ''), ']')
WHERE "key" = 'sso.oidc.oauth-scopes'
  AND "value" IS NOT NULL AND "value" <> '';
