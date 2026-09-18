CREATE TABLE IF NOT EXISTS application_logging_config (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES registered_application(id) ON DELETE CASCADE,
    logging_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    log_source VARCHAR(64) NOT NULL DEFAULT 'ELASTICSEARCH',
    index_pattern VARCHAR(128) NOT NULL DEFAULT 'prod-support-logs-*',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_logging_config_app UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_app_logging_config_app_id ON application_logging_config(application_id);

INSERT INTO application_logging_config (application_id, logging_enabled, log_source, index_pattern)
SELECT id, true, 'ELASTICSEARCH', 'prod-support-logs-*'
FROM registered_application
WHERE id NOT IN (SELECT application_id FROM application_logging_config);
