-- Flyway Migration: V2.3.0.6
-- Purpose: Add blurhash support to images for improved UI performance

-- Add blurhash column to IMAGE table
ALTER TABLE IMAGE ADD COLUMN BLURHASH VARCHAR(255);

-- Create alias for blurhash calculation helper
CREATE ALIAS IF NOT EXISTS CALCULATE_BLURHASHES_FOR_ALL_IMAGES FOR "org.gameyfin.db.h2.BlurhashMigration.calculateBlurhashesForAllImages";

-- Calculate blurhashes for all existing images
-- The data path is typically 'data' in the application root
-- Note: H2 automatically provides the Connection parameter to the Java method
CALL CALCULATE_BLURHASHES_FOR_ALL_IMAGES('data');

-- Drop the alias after use
DROP ALIAS IF EXISTS CALCULATE_BLURHASHES_FOR_ALL_IMAGES;

