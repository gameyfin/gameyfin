-- Flyway Migration: V2.3.0.2
-- Purpose: Add columns for library metadata fields to the LIBRARY table.
-- Compatibility: H2 2.2+ and PostgreSQL 13+ Fully compatible. Standard ALTER TABLE ADD COLUMN syntax.

ALTER TABLE LIBRARY
    ADD COLUMN DISPLAY_ON_HOMEPAGE BOOLEAN DEFAULT TRUE;

ALTER TABLE LIBRARY
    ADD COLUMN DISPLAY_ORDER INT DEFAULT -1;
