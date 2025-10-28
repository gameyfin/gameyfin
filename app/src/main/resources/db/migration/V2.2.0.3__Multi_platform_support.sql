-- Flyway Migration: V2.2.0.3
-- Purpose: Add LIBRARY_PLATFORMS and GAME_PLATFORMS tables to store list of associated platforms.
---         Add PLATFORM column to GAME_REQUEST table with default value for existing requests.
---         Default platform to 'PC (Microsoft Windows)' for existing records since that was the only supported platform before this change.

CREATE TABLE LIBRARY_PLATFORMS
(
    LIBRARY_ID BIGINT       NOT NULL,
    PLATFORMS  VARCHAR(255) NOT NULL,
    -- Composite primary key enforces uniqueness per library/platform
    CONSTRAINT PK_LIBRARY_PLATFORMS PRIMARY KEY (LIBRARY_ID, PLATFORMS),
    CONSTRAINT FK_LIBRARY_PLATFORMS_LIBRARY FOREIGN KEY (LIBRARY_ID) REFERENCES LIBRARY (ID) ON DELETE CASCADE,
    -- Prevent empty platform strings
    CONSTRAINT CK_LIBRARY_PLATFORMS_PLATFORM_NOT_EMPTY CHECK (TRIM(PLATFORMS) <> '')
);

CREATE TABLE GAME_PLATFORMS
(
    GAME_ID   BIGINT       NOT NULL,
    PLATFORMS VARCHAR(255) NOT NULL,
    -- Composite primary key enforces uniqueness per game/platform
    CONSTRAINT PK_GAME_PLATFORMS PRIMARY KEY (GAME_ID, PLATFORMS),
    CONSTRAINT FK_GAME_PLATFORMS_GAME FOREIGN KEY (GAME_ID) REFERENCES GAME (ID) ON DELETE CASCADE,
    -- Prevent empty platform strings
    CONSTRAINT CK_GAME_PLATFORMS_PLATFORM_NOT_EMPTY CHECK (TRIM(PLATFORMS) <> '')
);

-- Indexes to speed up lookups by parent IDs
CREATE INDEX IDX_LIBRARY_PLATFORMS_LIBRARY_ID ON LIBRARY_PLATFORMS (LIBRARY_ID);
CREATE INDEX IDX_GAME_PLATFORMS_GAME_ID ON GAME_PLATFORMS (GAME_ID);

-- Seed existing libraries with default Windows platform
INSERT INTO LIBRARY_PLATFORMS (LIBRARY_ID, PLATFORMS)
SELECT ID, 'PC_MICROSOFT_WINDOWS'
FROM LIBRARY;

-- Seed existing games with default Windows platform
INSERT INTO GAME_PLATFORMS (GAME_ID, PLATFORMS)
SELECT ID, 'PC_MICROSOFT_WINDOWS'
FROM GAME;

--- Seed existing game requests with default Windows platform
ALTER TABLE GAME_REQUEST
    ADD COLUMN PLATFORM VARCHAR(255)
        NOT NULL
        DEFAULT 'PC_MICROSOFT_WINDOWS';