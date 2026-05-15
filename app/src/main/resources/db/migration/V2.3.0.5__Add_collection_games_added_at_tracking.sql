-- Flyway Migration: V2.3.0.5
-- Purpose: Add tracking for when games are added to collections via COLLECTION_GAMES_ADDED_AT table

-- Create table to track when each game was added to a collection
CREATE TABLE COLLECTION_GAMES_ADDED_AT
(
    COLLECTION_ID      BIGINT                   NOT NULL,
    GAMES_ADDED_AT_KEY BIGINT                   NOT NULL,
    GAMES_ADDED_AT     TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (COLLECTION_ID, GAMES_ADDED_AT_KEY),
    CONSTRAINT FK_COLLECTION_GAMES_ADDED_AT_COLLECTION FOREIGN KEY (COLLECTION_ID) REFERENCES COLLECTION ON DELETE CASCADE
);

-- Create index for better performance on lookups
CREATE INDEX IDX_COLLECTION_GAMES_ADDED_AT_COLLECTION_ID ON COLLECTION_GAMES_ADDED_AT (COLLECTION_ID);

-- Initialize timestamps for existing collection-game relationships
-- Set the timestamp to the collection's created_at for all existing games
INSERT INTO COLLECTION_GAMES_ADDED_AT (COLLECTION_ID, GAMES_ADDED_AT_KEY, GAMES_ADDED_AT)
SELECT CG.COLLECTIONS_ID, CG.GAMES_ID, C.CREATED_AT
FROM COLLECTION_GAMES CG
         JOIN COLLECTION C ON CG.COLLECTIONS_ID = C.ID;

