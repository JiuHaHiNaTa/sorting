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

-- 分拣任务表
CREATE TABLE IF NOT EXISTS sorting_task (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    file_server_id VARCHAR(100),
    status VARCHAR(20),
    current_step VARCHAR(50),
    retry_count INT,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分拣任务备份表（已完成任务归档）
CREATE TABLE IF NOT EXISTS sorting_task_backup (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    file_server_id VARCHAR(100),
    status VARCHAR(20),
    current_step VARCHAR(50),
    retry_count INT,
    error_message TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    timeout_at TIMESTAMP,
    archived_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 分拣步骤日志表
CREATE TABLE IF NOT EXISTS sorting_step_log (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_id VARCHAR(36),
    step_name VARCHAR(50),
    status VARCHAR(20),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    detail TEXT
);
