-- Flyway Migration: V2.1.2
-- Purpose: Remove unintended single-column uniqueness on GAME_IMAGES.IMAGES_ID
--          (leftover unique constraint / index from initial schema) and
--          replace it with a proper composite uniqueness over (GAME_ID, IMAGES_ID)
--          allowing the same image to be linked to multiple games while
--          preventing duplicate pairs.
--
-- Context Recap:
--   * Initial table GAME_IMAGES had: IMAGES_ID UNIQUE (constraint UKBDE7M3TKHIEEYBINM2ED0B6X1)
--   * V2.1.0.1 only renamed that constraint (to UQ_GAME_IMAGES_IMAGE_ID if present) – did not drop it.
--   * Attempting to drop unique index now shows: "Index ... belongs to constraint FK_GAME_IMAGES_IMAGE".
--     This means H2 re-used (or bound) the existing unique index for the foreign key, so we must drop the FK first.
--   * Prior partial execution of an earlier draft of this migration might already have created
--     composite index UX_GAME_IMAGES_GAME_IMAGE. Script is idempotent.
-- Strategy (idempotent):
--   1. Drop foreign key FK_GAME_IMAGES_IMAGE (and legacy hashed name) to free the index.
--   2. Drop the old unique constraint names (hashed and friendly) if they still exist.
--   3. Drop lingering unique indexes (hashed + variants, including the one ending with _INDEX_C).
--   4. Create a NON-UNIQUE index on IMAGES_ID.
--   5. Create (or ensure) composite UNIQUE index (GAME_ID, IMAGES_ID).
--   6. Recreate foreign key FK_GAME_IMAGES_IMAGE.
--   7. (Optional) Verification queries shown in comments.

/******************************************************************************************
 * 1. Drop foreign key so bound unique index can be removed
 ******************************************************************************************/
ALTER TABLE GAME_IMAGES
    DROP CONSTRAINT IF EXISTS FK_GAME_IMAGES_IMAGE;
-- Legacy hashed name (in case rename migration not applied yet)
ALTER TABLE GAME_IMAGES
    DROP CONSTRAINT IF EXISTS FK5YWV1DMXCM2VSQUEB7RHQ3JK9;

/******************************************************************************************
 * 2. Drop legacy/friendly unique constraints (if still defined)
 ******************************************************************************************/
ALTER TABLE GAME_IMAGES
    DROP CONSTRAINT IF EXISTS UKBDE7M3TKHIEEYBINM2ED0B6X1; -- original hashed name
ALTER TABLE GAME_IMAGES
    DROP CONSTRAINT IF EXISTS UQ_GAME_IMAGES_IMAGE_ID;
-- friendly name

/******************************************************************************************
 * 3. Drop lingering unique indexes that may remain after constraint drop
 *    (H2 auto-named variants; include conservative list). Safe if absent.
 ******************************************************************************************/
DROP INDEX IF EXISTS UKBDE7M3TKHIEEYBINM2ED0B6X1_INDEX_C;

/******************************************************************************************
 * 4. Create supporting NON-UNIQUE index for IMAGES_ID (only if missing)
 ******************************************************************************************/
CREATE INDEX IF NOT EXISTS IDX_GAME_IMAGES_IMAGE ON GAME_IMAGES (IMAGES_ID);

/******************************************************************************************
 * 5. Create / ensure composite uniqueness (prevents duplicate pairs, allows reuse of images)
 ******************************************************************************************/
CREATE UNIQUE INDEX IF NOT EXISTS UX_GAME_IMAGES_GAME_IMAGE ON GAME_IMAGES (GAME_ID, IMAGES_ID);

/******************************************************************************************
 * 6. Recreate foreign key (H2 will use existing non-unique index or create one silently)
 ******************************************************************************************/
ALTER TABLE GAME_IMAGES
    ADD CONSTRAINT FK_GAME_IMAGES_IMAGE FOREIGN KEY (IMAGES_ID) REFERENCES IMAGE (ID);
-- (FK to GAME side should already exist; keep idempotent recreation separate if ever needed.)

/******************************************************************************************
 * 7. (Optional verification after migration)
 * -- SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.CONSTRAINTS WHERE TABLE_NAME='GAME_IMAGES';
 * -- SELECT INDEX_NAME, NON_UNIQUE, COLUMN_NAME FROM INFORMATION_SCHEMA.INDEXES WHERE TABLE_NAME='GAME_IMAGES';
 * Expected:
 *   Constraint FK_GAME_IMAGES_IMAGE present (TYPE='REFERENTIAL').
 *   Composite unique index UX_GAME_IMAGES_GAME_IMAGE (NON_UNIQUE=FALSE, columns GAME_ID, IMAGES_ID).
 *   Non-unique index IDX_GAME_IMAGES_IMAGE (NON_UNIQUE=TRUE, column IMAGES_ID).
 *   No remaining single-column unique index enforcing uniqueness of IMAGES_ID alone.
 ******************************************************************************************/
-- End of migration.
