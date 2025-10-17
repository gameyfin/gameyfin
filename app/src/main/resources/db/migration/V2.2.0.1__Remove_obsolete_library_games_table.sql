-- Flyway Migration: V2.2.0
-- Purpose: Remove obsolete LIBRARY_GAMES join table and backfill GAME.LIBRARY_ID if needed.

-- 1) Backfill GAME.LIBRARY_ID from LIBRARY_GAMES when null (safety for older data)
UPDATE GAME
SET LIBRARY_ID = (
    SELECT LIBRARY_ID FROM LIBRARY_GAMES LG WHERE LG.GAMES_ID = GAME.ID
    )
WHERE LIBRARY_ID IS NULL
  AND EXISTS (SELECT 1
              FROM LIBRARY_GAMES LG
              WHERE LG.GAMES_ID = GAME.ID);

-- 2) Drop the obsolete join table
DROP TABLE IF EXISTS LIBRARY_GAMES;

-- 3) Create index on GAME.LIBRARY_ID for performance
CREATE INDEX IF NOT EXISTS IDX_GAME_LIBRARY_ID ON GAME (LIBRARY_ID);