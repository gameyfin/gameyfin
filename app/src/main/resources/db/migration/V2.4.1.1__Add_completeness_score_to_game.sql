-- Flyway Migration: V2.4.1.1
-- Purpose: Add COMPLETENESS_SCORE column to the GAME table and back-fill the value for all
--          existing rows using the same 17-field logic as Game.calculateCompletenessScore().
-- Formula: (number_of_non_empty_fields * 100) / 17  (integer division)
-- Fields evaluated (matches Game.calculateCompletenessScore — excludes COMMENT, COLLECTIONS, METADATA):
--   1  TITLE              not null / not blank
--   2  COVER_IMAGE_ID     not null
--   3  HEADER_IMAGE_ID    not null
--   4  SUMMARY            not null / not blank
--   5  RELEASE            not null
--   6  USER_RATING        not null
--   7  CRITIC_RATING      not null
--   8  GAME_PLATFORMS     has at least one row
--   9  GAME_PUBLISHERS    has at least one row
--  10  GAME_DEVELOPERS    has at least one row
--  11  GAME_GENRES        has at least one row
--  12  GAME_THEMES        has at least one row
--  13  GAME_KEYWORDS      has at least one row
--  14  GAME_FEATURES      has at least one row
--  15  GAME_PERSPECTIVES  has at least one row
--  16  GAME_IMAGES        has at least one row
--  17  GAME_VIDEO_URLS    has at least one row

ALTER TABLE GAME
    ADD COLUMN COMPLETENESS_SCORE INTEGER NOT NULL DEFAULT 0;

MERGE INTO GAME AS g
USING (
    SELECT
        g2.ID,
        (
            -- 1. Title
            CASE WHEN g2.TITLE IS NOT NULL AND TRIM(g2.TITLE) <> '' THEN 1 ELSE 0 END +
            -- 2. Cover image
            CASE WHEN g2.COVER_IMAGE_ID IS NOT NULL THEN 1 ELSE 0 END +
            -- 3. Header image
            CASE WHEN g2.HEADER_IMAGE_ID IS NOT NULL THEN 1 ELSE 0 END +
            -- 4. Summary (CLOB — cast to VARCHAR for TRIM)
            CASE WHEN g2.SUMMARY IS NOT NULL AND LENGTH(TRIM(CAST(g2.SUMMARY AS VARCHAR))) > 0 THEN 1 ELSE 0 END +
            -- 5. Release date
            CASE WHEN g2.RELEASE IS NOT NULL THEN 1 ELSE 0 END +
            -- 6. User rating
            CASE WHEN g2.USER_RATING IS NOT NULL THEN 1 ELSE 0 END +
            -- 7. Critic rating
            CASE WHEN g2.CRITIC_RATING IS NOT NULL THEN 1 ELSE 0 END +
            -- 8–17. Collection tables: each pre-aggregated to one row per GAME_ID
            COALESCE(plt.flag,  0) +
            COALESCE(pub.flag,  0) +
            COALESCE(dev.flag,  0) +
            COALESCE(gen.flag,  0) +
            COALESCE(thm.flag,  0) +
            COALESCE(kw.flag,   0) +
            COALESCE(feat.flag, 0) +
            COALESCE(persp.flag,0) +
            COALESCE(img.flag,  0) +
            COALESCE(vid.flag,  0)
        ) * 100 / 17 AS score
    FROM GAME g2
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_PLATFORMS    GROUP BY GAME_ID) plt   ON g2.ID = plt.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_PUBLISHERS   GROUP BY GAME_ID) pub   ON g2.ID = pub.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_DEVELOPERS   GROUP BY GAME_ID) dev   ON g2.ID = dev.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_GENRES       GROUP BY GAME_ID) gen   ON g2.ID = gen.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_THEMES       GROUP BY GAME_ID) thm   ON g2.ID = thm.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_KEYWORDS     GROUP BY GAME_ID) kw    ON g2.ID = kw.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_FEATURES     GROUP BY GAME_ID) feat  ON g2.ID = feat.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_PERSPECTIVES GROUP BY GAME_ID) persp ON g2.ID = persp.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_IMAGES       GROUP BY GAME_ID) img   ON g2.ID = img.GAME_ID
    LEFT JOIN (SELECT GAME_ID, 1 AS flag FROM GAME_VIDEO_URLS   GROUP BY GAME_ID) vid   ON g2.ID = vid.GAME_ID
) AS scored ON g.ID = scored.ID
WHEN MATCHED THEN UPDATE SET g.COMPLETENESS_SCORE = scored.score;


