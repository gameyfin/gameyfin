-- Flyway Migration: V2.1.1
-- Purpose: Fully eliminate unintended uniqueness on GAME.COVER_IMAGE_ID / HEADER_IMAGE_ID
--          by dropping and recreating foreign keys and removing lingering unique indexes.
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - DROP INDEX IF EXISTS is valid PG syntax but indexes are schema-scoped, not table-scoped.
--   - H2 auto-named indexes like UK52..._INDEX_2 won't exist in PostgreSQL; those DROP INDEX
--     lines are kept as no-ops (IF EXISTS ensures no failure).
--   - All other syntax is standard and compatible.

/******************************************************************************************
 * 1. Drop foreign keys so their backing indexes can be dropped safely
 ******************************************************************************************/
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS FK_GAME_COVER_IMAGE;
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS FK_GAME_HEADER_IMAGE;

-- Also attempt legacy hashed names (in case rename earlier never ran)
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS FK6CVB43REAYSNYPI0XDY6HQTVF;
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS FK8N86NDPGKMOO7YOLX6HL8N84G;

/******************************************************************************************
 * 2. Drop any lingering UNIQUE constraints (defensive) and their indexes
 ******************************************************************************************/
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS UK52RQ62FLPBNTI77BYKM7UAHKQ;
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS UK30B16LLQV54H40XIOGP7T9P35;
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS UQ_GAME_COVER_IMAGE_ID;
ALTER TABLE GAME DROP CONSTRAINT IF EXISTS UQ_GAME_HEADER_IMAGE_ID;

-- These H2-specific auto-named indexes won't exist in PG; safe no-ops due to IF EXISTS
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
ALTER TABLE GAME ADD CONSTRAINT FK_GAME_COVER_IMAGE FOREIGN KEY (COVER_IMAGE_ID) REFERENCES IMAGE (ID);
ALTER TABLE GAME ADD CONSTRAINT FK_GAME_HEADER_IMAGE FOREIGN KEY (HEADER_IMAGE_ID) REFERENCES IMAGE (ID);

-- End of migration.
