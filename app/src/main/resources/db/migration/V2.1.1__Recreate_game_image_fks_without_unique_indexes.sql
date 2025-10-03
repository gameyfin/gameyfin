-- Flyway Migration: V2.1.1
-- Purpose: Fully eliminate unintended uniqueness on GAME.COVER_IMAGE_ID / HEADER_IMAGE_ID
--          by dropping and recreating foreign keys and removing lingering unique indexes
-- Context:
--   * Original schema created UNIQUE constraints (UK52... cover, UK30... header).
--   * V2.1.0.1 dropped those constraints but H2 left behind unique indexes (UK52..._INDEX_n etc.).
-- Strategy:
--   1. Drop the foreign keys (idempotent).
--   2. Drop any remaining unique constraints (defensive repeat) and their indexes.
--   3. Recreate NON-UNIQUE supporting indexes explicitly (optional but good for lookups).
--   4. Recreate the foreign keys cleanly without reintroducing uniqueness.
--   5. All steps are idempotent / tolerant so reruns don't fail.

/******************************************************************************************
 * 1. Drop foreign keys so their backing indexes can be dropped safely
 ******************************************************************************************/
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS FK_GAME_COVER_IMAGE;
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS FK_GAME_HEADER_IMAGE;

-- Also attempt legacy hashed names (in case rename earlier never ran)
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS FK6CVB43REAYSNYPI0XDY6HQTVF; -- old cover FK
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS FK8N86NDPGKMOO7YOLX6HL8N84G;
-- old header FK

/******************************************************************************************
 * 2. Drop any lingering UNIQUE constraints again (defensive) and their indexes
 ******************************************************************************************/
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS UK52RQ62FLPBNTI77BYKM7UAHKQ; -- old unique cover
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS UK30B16LLQV54H40XIOGP7T9P35; -- old unique header
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS UQ_GAME_COVER_IMAGE_ID; -- friendly (future) name
ALTER TABLE GAME
    DROP CONSTRAINT IF EXISTS UQ_GAME_HEADER_IMAGE_ID;
-- friendly (future) name

-- Drop possible leftover unique indexes (multiple variants tried)
DROP INDEX IF EXISTS UK52RQ62FLPBNTI77BYKM7UAHKQ_INDEX_2;
DROP INDEX IF EXISTS UK52RQ62FLPBNTI77BYKM7UAHKQ_INDEX_1;
DROP INDEX IF EXISTS UK30B16LLQV54H40XIOGP7T9P35_INDEX_2;
DROP INDEX IF EXISTS UK30B16LLQV54H40XIOGP7T9P35_INDEX_1;

/******************************************************************************************
 * 3. Create explicit NON-UNIQUE indexes (only if missing)
 ******************************************************************************************/
CREATE INDEX IF NOT EXISTS IDX_GAME_COVER_IMAGE ON GAME (COVER_IMAGE_ID);
CREATE INDEX IF NOT EXISTS IDX_GAME_HEADER_IMAGE ON GAME (HEADER_IMAGE_ID);

/******************************************************************************************
 * 4. Recreate foreign keys (non-unique by definition)
 ******************************************************************************************/
ALTER TABLE GAME
    ADD CONSTRAINT FK_GAME_COVER_IMAGE FOREIGN KEY (COVER_IMAGE_ID) REFERENCES IMAGE (ID);
ALTER TABLE GAME
    ADD CONSTRAINT FK_GAME_HEADER_IMAGE FOREIGN KEY (HEADER_IMAGE_ID) REFERENCES IMAGE (ID);

/******************************************************************************************
 * 5. (Optional manual verification after migration)
 * -- SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM INFORMATION_SCHEMA.INDEXES
 * --  WHERE TABLE_NAME='GAME' AND COLUMN_NAME IN ('COVER_IMAGE_ID','HEADER_IMAGE_ID');
 * Expected: ONLY non-unique indexes (NON_UNIQUE=TRUE) for those columns.
 ******************************************************************************************/

-- End of migration.

