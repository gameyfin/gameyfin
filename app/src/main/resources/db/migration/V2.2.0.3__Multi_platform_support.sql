-- Flyway Migration: V2.2.0.3
-- Purpose: Add LIBRARY_PLATFORMS and GAME_PLATFORMS tables to store list of associated platforms.
---         Add PLATFORM column to GAME_REQUEST table with default value for existing requests.

CREATE TABLE LIBRARY_PLATFORMS
(
    LIBRARY_ID BIGINT       NOT NULL,
    PLATFORMS  VARCHAR(255) NOT NULL,
    CONSTRAINT FK_LIBRARY_PLATFORMS_LIBRARY FOREIGN KEY (LIBRARY_ID) REFERENCES LIBRARY
);

CREATE TABLE GAME_PLATFORMS
(
    GAME_ID   BIGINT       NOT NULL,
    PLATFORMS VARCHAR(255) NOT NULL,
    CONSTRAINT FK_GAME_PLATFORMS_GAME FOREIGN KEY (GAME_ID) REFERENCES GAME
);

--- Default platform to 'PC (Microsoft Windows)' for existing records since that was the only supported platform before this change.
ALTER TABLE GAME_REQUEST
    ADD COLUMN PLATFORM VARCHAR(255)
        NOT NULL
        DEFAULT 'PC_MICROSOFT_WINDOWS';