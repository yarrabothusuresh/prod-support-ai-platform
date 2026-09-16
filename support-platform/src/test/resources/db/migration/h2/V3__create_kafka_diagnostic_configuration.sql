CREATE TABLE IF NOT EXISTS application_kafka_config (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES registered_application(id) ON DELETE CASCADE,
    bootstrap_servers VARCHAR(500) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_app_kafka_config_app UNIQUE (application_id)
);

CREATE TABLE IF NOT EXISTS application_kafka_consumer_group (
    id BIGSERIAL PRIMARY KEY,
    kafka_config_id BIGINT NOT NULL REFERENCES application_kafka_config(id) ON DELETE CASCADE,
    consumer_group VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_app_kafka_cg UNIQUE (kafka_config_id, consumer_group)
);

CREATE TABLE IF NOT EXISTS application_kafka_topic (
    id BIGSERIAL PRIMARY KEY,
    kafka_config_id BIGINT NOT NULL REFERENCES application_kafka_config(id) ON DELETE CASCADE,
    topic_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_app_kafka_topic UNIQUE (kafka_config_id, topic_name)
);

CREATE INDEX IF NOT EXISTS idx_app_kafka_cg_config_id ON application_kafka_consumer_group(kafka_config_id);
CREATE INDEX IF NOT EXISTS idx_app_kafka_topic_config_id ON application_kafka_topic(kafka_config_id);
