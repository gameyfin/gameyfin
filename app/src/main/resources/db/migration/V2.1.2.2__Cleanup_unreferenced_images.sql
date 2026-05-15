-- Flyway Migration: V2.1.2.2
-- Purpose: Remove orphan (unreferenced) IMAGE rows that are no longer linked to any
--          GAME (cover/header), GAME_IMAGES (many-to-many screenshots), or USERS (avatar).
-- Compatibility: H2 2.2+ and PostgreSQL 13+ This migration is fully compatible with PostgreSQL as-is.
--   Standard DELETE ... WHERE NOT EXISTS syntax works identically.

DELETE FROM IMAGE i
WHERE NOT EXISTS (SELECT 1 FROM GAME g WHERE g.COVER_IMAGE_ID = i.ID)
  AND NOT EXISTS (SELECT 1 FROM GAME g2 WHERE g2.HEADER_IMAGE_ID = i.ID)
  AND NOT EXISTS (SELECT 1 FROM GAME_IMAGES gi WHERE gi.IMAGES_ID = i.ID)
  AND NOT EXISTS (SELECT 1 FROM USERS u WHERE u.AVATAR_ID = i.ID);

-- End of migration.
