-- Add field "addedToLibrary" to the "DetectedGame" table with the default value of CURRENT_TIMESTAMP()

alter table DETECTED_GAME
add added_to_library timestamp not null default CURRENT_TIMESTAMP()