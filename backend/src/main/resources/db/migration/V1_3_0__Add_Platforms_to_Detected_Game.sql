-- Add platforms

-- Platforms
CREATE TABLE platform
(
    slug VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    logo_id VARCHAR(255),
    PRIMARY KEY (slug)
);

-- Game <-> Platforms
CREATE TABLE detected_game_platforms
(
    detected_game_slug VARCHAR(255) NOT NULL,
    platforms_slug     VARCHAR(255) NOT NULL
);
ALTER TABLE detected_game_platforms
    ADD CONSTRAINT platforms_platform_slug FOREIGN KEY (platforms_slug) REFERENCES platform;
ALTER TABLE detected_game_platforms
    ADD CONSTRAINT platforms_detected_game_slug FOREIGN KEY (detected_game_slug) REFERENCES detected_game;

-- Add libraries

-- Libraries
CREATE TABLE library
(
    path VARCHAR(255) NOT NULL,
    PRIMARY KEY (path)
);

-- Library <-> Platforms
CREATE TABLE library_platforms
(
    library_path   VARCHAR(255) NOT NULL,
    platforms_slug VARCHAR(255) NOT NULL
);
ALTER TABLE library_platforms
    ADD CONSTRAINT libraries_platform_slug FOREIGN KEY (platforms_slug) REFERENCES platform;
ALTER TABLE library_platforms
    ADD CONSTRAINT libraries_library_path FOREIGN KEY (library_path) REFERENCES library;

-- Library <-> Game
ALTER TABLE detected_game
    ADD library VARCHAR(255);