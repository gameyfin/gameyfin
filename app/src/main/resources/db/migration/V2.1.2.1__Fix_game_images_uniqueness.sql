-- Flyway Migration: V2.1.2.1
-- Purpose: Remove unintended single-column uniqueness on GAME_IMAGES.IMAGES_ID
--          and replace with composite uniqueness over (GAME_ID, IMAGES_ID).
-- Compatibility: H2 2.2+ and PostgreSQL 13+
--   - All syntax is standard and compatible with PostgreSQL.
--   - H2-specific auto-named index variants (UKBDE7..._INDEX_C etc.) won't exist in PG;
--     DROP INDEX IF EXISTS ensures safe no-ops.
--   - INFORMATION_SCHEMA queries in comments updated to PG equivalents for reference.

/******************************************************************************************
 * 1. Drop foreign key so bound unique index can be removed
 ******************************************************************************************/
ALTER TABLE GAME_IMAGES DROP CONSTRAINT IF EXISTS FK_GAME_IMAGES_IMAGE;
-- Legacy hashed name (in case rename migration not applied yet)
ALTER TABLE GAME_IMAGES DROP CONSTRAINT IF EXISTS FK5YWV1DMXCM2VSQUEB7RHQ3JK9;

/******************************************************************************************
 * 2. Drop legacy/friendly unique constraints (if still defined)
 ******************************************************************************************/
ALTER TABLE GAME_IMAGES DROP CONSTRAINT IF EXISTS UKBDE7M3TKHIEEYBINM2ED0B6X1;
ALTER TABLE GAME_IMAGES DROP CONSTRAINT IF EXISTS UQ_GAME_IMAGES_IMAGE_ID;

/******************************************************************************************
 * 3. Drop lingering unique indexes (H2 auto-named; safe no-ops in PG)
 ******************************************************************************************/
DROP INDEX IF EXISTS UKBDE7M3TKHIEEYBINM2ED0B6X1_INDEX_C;

/******************************************************************************************
 * 4. Create supporting NON-UNIQUE index for IMAGES_ID (only if missing)
 ******************************************************************************************/
CREATE INDEX IF NOT EXISTS IDX_GAME_IMAGES_IMAGE ON GAME_IMAGES (IMAGES_ID);

/******************************************************************************************
 * 5. Create / ensure composite uniqueness (prevents duplicate pairs)
 ******************************************************************************************/
CREATE UNIQUE INDEX IF NOT EXISTS UX_GAME_IMAGES_GAME_IMAGE ON GAME_IMAGES (GAME_ID, IMAGES_ID);

/******************************************************************************************
 * 6. Recreate foreign key
 ******************************************************************************************/
ALTER TABLE GAME_IMAGES ADD CONSTRAINT FK_GAME_IMAGES_IMAGE FOREIGN KEY (IMAGES_ID) REFERENCES IMAGE (ID);

/******************************************************************************************
 * 7. (Optional verification after migration — PostgreSQL equivalents)
 * -- SELECT conname, contype FROM pg_constraint WHERE conrelid = 'game_images'::regclass;
 * -- SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'game_images';
 ******************************************************************************************/
-- End of migration.
