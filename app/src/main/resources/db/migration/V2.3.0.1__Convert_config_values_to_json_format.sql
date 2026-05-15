-- Flyway Migration: V2.3.0.1
-- Purpose: Convert config values from old format to JSON format for consistency
-- Context: ConfigService now uses ObjectMapper for all serialization/deserialization.
--          Old formats were:
--          - Primitives (String, Boolean, Int, Float): stored as plain strings
--          - Enums: stored as plain strings
--          - Arrays: stored as comma-separated values
--          New format: Everything stored as JSON

-- Create aliases for conversion functions
CREATE ALIAS IF NOT EXISTS TO_JSON_STRING FOR "org.gameyfin.db.h2.H2Aliases.toJsonString";
CREATE ALIAS IF NOT EXISTS TO_JSON_BOOLEAN FOR "org.gameyfin.db.h2.H2Aliases.toJsonBoolean";
CREATE ALIAS IF NOT EXISTS TO_JSON_INT FOR "org.gameyfin.db.h2.H2Aliases.toJsonInt";
CREATE ALIAS IF NOT EXISTS TO_JSON_ARRAY FOR "org.gameyfin.db.h2.H2Aliases.toJsonArray";

-- Convert String values to JSON format (wrap in quotes)
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'logs.folder';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'library.scan.title-extraction-regex';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'library.metadata.update.schedule';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'messages.providers.email.host';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'messages.providers.email.username';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'messages.providers.email.password';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.roles-claim';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.client-id';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.client-secret';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.issuer-url';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.authorize-url';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.token-url';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.userinfo-url';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.jwks-url';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.logout-url';

-- Convert Boolean values to JSON format (true/false without quotes)
-- Note: These are likely already in correct format, but function is idempotent
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'library.allow-public-access';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'library.scan.enable-filesystem-watcher';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'library.scan.scan-empty-directories';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'library.scan.extract-title-using-regex';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'library.metadata.update.enabled';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'requests.games.enabled';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'requests.games.allow-guests-to-request-games';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'downloads.bandwidth-limit.enabled';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'users.sign-ups.allow';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'users.sign-ups.confirmation-required';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'sso.oidc.enabled';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'sso.oidc.auto-register-new-users';
UPDATE APP_CONFIG SET "value" = TO_JSON_BOOLEAN("value") WHERE "key" = 'messages.providers.email.enabled';

-- Convert Int values to JSON format (plain numbers)
-- Note: These are likely already in correct format, but function is idempotent
UPDATE APP_CONFIG SET "value" = TO_JSON_INT("value") WHERE "key" = 'logs.max-history-days';
UPDATE APP_CONFIG SET "value" = TO_JSON_INT("value") WHERE "key" = 'library.scan.title-match-min-ratio';
UPDATE APP_CONFIG SET "value" = TO_JSON_INT("value") WHERE "key" = 'requests.games.max-open-requests-per-user';
UPDATE APP_CONFIG SET "value" = TO_JSON_INT("value") WHERE "key" = 'downloads.bandwidth-limit.mbps';
UPDATE APP_CONFIG SET "value" = TO_JSON_INT("value") WHERE "key" = 'messages.providers.email.port';

-- Convert Enum values to JSON format (quoted strings)
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'logs.level.gameyfin';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'logs.level.root';
UPDATE APP_CONFIG SET "value" = TO_JSON_STRING("value") WHERE "key" = 'sso.oidc.match-existing-users-by';

-- Convert Array values to JSON format (from comma-separated to JSON array)
UPDATE APP_CONFIG SET "value" = TO_JSON_ARRAY("value") WHERE "key" = 'library.scan.game-file-extensions';
UPDATE APP_CONFIG SET "value" = TO_JSON_ARRAY("value") WHERE "key" = 'sso.oidc.oauth-scopes';