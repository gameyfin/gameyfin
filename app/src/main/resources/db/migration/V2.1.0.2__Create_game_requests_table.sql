-- Flyway Migration: V2.1.0.2
-- Purpose:
-- 1. Create tables for the game requests feature
-- Compatibility: H2 2.2+ and PostgreSQL 13+ No changes required. TIMESTAMP, VARCHAR, BIGINT, and standard FK
--   syntax are all valid PostgreSQL. IF NOT EXISTS is supported.

/******************************************************************************************
 * 1. Create new sequence
 ******************************************************************************************/
CREATE SEQUENCE IF NOT EXISTS GAME_REQUEST_SEQ
    INCREMENT BY 50;

/******************************************************************************************
 * 2. Create new tables
 ******************************************************************************************/
CREATE TABLE IF NOT EXISTS GAME_REQUEST
(
    ID             BIGINT       NOT NULL PRIMARY KEY,
    TITLE          VARCHAR(255) NOT NULL,
    RELEASE        TIMESTAMP WITH TIME ZONE NOT NULL,
    STATUS         VARCHAR(255) NOT NULL,
    REQUESTER_ID   BIGINT,
    LINKED_GAME_ID BIGINT,
    CREATED_AT     TIMESTAMP WITH TIME ZONE NOT NULL,
    UPDATED_AT     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT FK_GAMEREQUEST_ON_REQUESTER
        FOREIGN KEY (REQUESTER_ID) REFERENCES USERS
            ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS GAME_REQUEST_VOTERS
(
    GAME_REQUEST_ID BIGINT NOT NULL,
    VOTERS_ID       BIGINT NOT NULL,
    PRIMARY KEY (GAME_REQUEST_ID, VOTERS_ID),
    CONSTRAINT FK_GAMREQVOT_ON_GAME_REQUEST
        FOREIGN KEY (GAME_REQUEST_ID) REFERENCES GAME_REQUEST
            ON DELETE CASCADE,
    CONSTRAINT FK_GAMREQVOT_ON_USER
        FOREIGN KEY (VOTERS_ID) REFERENCES USERS
);

-- End of migration
