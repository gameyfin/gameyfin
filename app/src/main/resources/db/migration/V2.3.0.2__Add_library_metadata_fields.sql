-- Flyway Migration: V2.3.0.2
-- Purpose: Add columns for library metadata fields to the LIBRARY table.
-- Context: These fields will store additional information about each library.

--- Add COLUMN for "displayOnHomepage"
ALTER TABLE LIBRARY
    ADD COLUMN DISPLAY_ON_HOMEPAGE BOOLEAN DEFAULT TRUE;

--- Add COLUMN for "displayOrder"
ALTER TABLE LIBRARY
    ADD COLUMN DISPLAY_ORDER INT DEFAULT -1;