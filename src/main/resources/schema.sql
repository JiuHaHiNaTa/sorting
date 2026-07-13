CREATE TABLE IF NOT EXISTS file_server_config (
    id                   VARCHAR(36)   PRIMARY KEY,
    server_address       VARCHAR(255)  NOT NULL,
    server_port          VARCHAR(10)   NOT NULL,
    bucket_name          VARCHAR(255)  NOT NULL,
    access_key           VARCHAR(512)  NOT NULL,
    secret_key           VARCHAR(512)  NOT NULL,
    file_directory       VARCHAR(500)  NOT NULL,
    connectivity_status  BOOLEAN       DEFAULT FALSE NOT NULL,
    enabled              BOOLEAN       DEFAULT FALSE NOT NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
