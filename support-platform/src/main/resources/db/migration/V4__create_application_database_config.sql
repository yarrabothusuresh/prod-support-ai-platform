CREATE TABLE IF NOT EXISTS application_database_config (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES registered_application(id) ON DELETE CASCADE,
    database_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    jdbc_url VARCHAR(512) NOT NULL,
    username VARCHAR(128) NOT NULL,
    credential_reference VARCHAR(128),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_db_config_app UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_app_db_config_app_id ON application_database_config(application_id);
