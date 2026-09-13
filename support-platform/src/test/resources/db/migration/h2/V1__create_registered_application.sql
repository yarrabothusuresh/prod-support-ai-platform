CREATE TABLE registered_application (
    id BIGSERIAL PRIMARY KEY,
    application_name VARCHAR(100) NOT NULL,
    team VARCHAR(100),
    environment VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    base_url VARCHAR(255) NOT NULL,
    health_url VARCHAR(255) NOT NULL,
    support_info_url VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_registered_application_name_env UNIQUE (application_name, environment)
);
