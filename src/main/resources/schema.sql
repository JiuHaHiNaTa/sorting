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

CREATE TABLE IF NOT EXISTS operator (
    id          VARCHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)     NOT NULL UNIQUE,
    name        VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS service_az (
    id          VARCHAR(36)     PRIMARY KEY,
    code        VARCHAR(50)     NOT NULL UNIQUE,
    name        VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS usage_unit (
    id          VARCHAR(36)     PRIMARY KEY,
    code        VARCHAR(20)     NOT NULL UNIQUE,
    name        VARCHAR(50)    NOT NULL,
    created_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sorting_task (
    id              VARCHAR(36)     PRIMARY KEY,
    file_server_id  VARCHAR(36)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    current_step    VARCHAR(30),
    retry_count     INT             DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    timeout_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sorting_task_backup (
    id              VARCHAR(36)     PRIMARY KEY,
    file_server_id  VARCHAR(36)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    current_step    VARCHAR(30),
    retry_count     INT             DEFAULT 0,
    error_message   TEXT,
    started_at      TIMESTAMP,
    completed_at    TIMESTAMP,
    timeout_at      TIMESTAMP,
    archived_at     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cdr_record (
    id              VARCHAR(36)     PRIMARY KEY,
    task_id         VARCHAR(36)     NOT NULL,
    operator_id     VARCHAR(36)     NOT NULL,
    service_az_id   VARCHAR(36)     NOT NULL,
    resource_id     VARCHAR(36)     NOT NULL,
    usage_amount    DECIMAL(18,4)   NOT NULL,
    usage_unit_id   VARCHAR(36)     NOT NULL,
    start_time      TIMESTAMP       NOT NULL,
    end_time        TIMESTAMP       NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sorting_step_log (
    id              VARCHAR(36)     PRIMARY KEY,
    task_id         VARCHAR(36)     NOT NULL,
    step_name       VARCHAR(30)     NOT NULL,
    status          VARCHAR(20)     NOT NULL,
    started_at      TIMESTAMP       NOT NULL,
    completed_at    TIMESTAMP,
    detail          TEXT
);
