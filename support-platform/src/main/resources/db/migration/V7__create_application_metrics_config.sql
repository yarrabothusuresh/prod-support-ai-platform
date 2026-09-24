CREATE TABLE IF NOT EXISTS application_metrics_config (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES registered_application(id) ON DELETE CASCADE,
    metrics_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    prometheus_job VARCHAR(100) NOT NULL,
    application_label VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_metrics_config_app UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_app_metrics_config_app_id ON application_metrics_config(application_id);

-- Populate metrics configuration for existing registered applications
INSERT INTO application_metrics_config (application_id, metrics_enabled, prometheus_job, application_label)
SELECT id, true, application_name, application_name
FROM registered_application
WHERE id NOT IN (SELECT application_id FROM application_metrics_config);
