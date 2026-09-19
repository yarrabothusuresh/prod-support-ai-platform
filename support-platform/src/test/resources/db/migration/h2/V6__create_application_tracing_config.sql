CREATE TABLE IF NOT EXISTS application_tracing_config (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES registered_application(id) ON DELETE CASCADE,
    tracing_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    tracing_service_name VARCHAR(100) NOT NULL,
    tracing_environment VARCHAR(50) NOT NULL,
    jaeger_query_base_url VARCHAR(255) NOT NULL DEFAULT 'http://localhost:16686',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_tracing_config_app UNIQUE (application_id)
);

CREATE INDEX IF NOT EXISTS idx_app_tracing_config_app_id ON application_tracing_config(application_id);

INSERT INTO application_tracing_config (application_id, tracing_enabled, tracing_service_name, tracing_environment, jaeger_query_base_url)
SELECT id, true, application_name, environment, 'http://localhost:16686'
FROM registered_application
WHERE id NOT IN (SELECT application_id FROM application_tracing_config);
