CREATE TABLE google_oauth_flows (
    id UUID PRIMARY KEY,
    poll_secret_hash VARCHAR(64) NOT NULL UNIQUE,
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    nonce_hash VARCHAR(64) NOT NULL,
    device_label VARCHAR(160) NOT NULL,
    flow_status VARCHAR(20) NOT NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    failure_message VARCHAR(300),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    consumed_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_google_oauth_flow_expiry
    ON google_oauth_flows(flow_status, expires_at);

INSERT INTO schema_migrations(migration_key, applied_at, details)
VALUES ('google-oauth-v3', CURRENT_TIMESTAMP,
        'One-time hashed browser OAuth state and desktop handoff');
