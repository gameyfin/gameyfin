-- Flyway Migration: V2.1.2.2
-- Purpose: Remove orphan (unreferenced) IMAGE rows that are no longer linked to any
--          GAME (cover/header), GAME_IMAGES (many-to-many screenshots), or USERS (avatar).
--
-- Rationale:
--   A previous bug deleted content (files) before deleting the DB row, allowing the
--   IMAGE entity to remain referenced or resurrected. After fixing logic order, we
--   now perform a one-time cleanup of rows that have no remaining foreign key references.
--
-- Safety:
--   The DELETE only targets rows for which no referencing rows exist; it will not
--   violate FK constraints. Uses NOT EXISTS predicates (safer than NOT IN when NULLs present).
--
-- Idempotency:
--   Running this migration again (e.g., in replayed environments) is harmless because
--   once removed, those rows no longer exist.
--
-- Verification (optional; run manually):
--   SELECT COUNT(*) FROM IMAGE i
--     WHERE NOT EXISTS (SELECT 1 FROM GAME g WHERE g.COVER_IMAGE_ID = i.ID)
--       AND NOT EXISTS (SELECT 1 FROM GAME g2 WHERE g2.HEADER_IMAGE_ID = i.ID)
--       AND NOT EXISTS (SELECT 1 FROM GAME_IMAGES gi WHERE gi.IMAGES_ID = i.ID)
--       AND NOT EXISTS (SELECT 1 FROM USERS u WHERE u.AVATAR_ID = i.ID);
--   -- Expect 0 after delete.

DELETE FROM IMAGE i
WHERE NOT EXISTS (SELECT 1 FROM GAME g WHERE g.COVER_IMAGE_ID = i.ID)
  AND NOT EXISTS (SELECT 1 FROM GAME g2 WHERE g2.HEADER_IMAGE_ID = i.ID)
  AND NOT EXISTS (SELECT 1 FROM GAME_IMAGES gi WHERE gi.IMAGES_ID = i.ID)
  AND NOT EXISTS (SELECT 1 FROM USERS u WHERE u.AVATAR_ID = i.ID);

-- End of migration.

