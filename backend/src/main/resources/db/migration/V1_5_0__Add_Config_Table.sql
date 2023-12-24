-- Add GameyfinConfig

-- GameyfinConfig
CREATE TABLE gameyfin_config
(
    config_key   CHAR VARYING NOT NULL,
    config_value CHAR VARYING,
    type         CHAR VARYING,
    PRIMARY KEY (config_key)
);

-- Insert SETUP_COMPLETED key
INSERT INTO gameyfin_config
VALUES ('SETUP_COMPLETED', 'false', 'java.lang.Boolean');
