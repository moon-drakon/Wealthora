CREATE TABLE users (
    id UUID PRIMARY KEY,
    full_name VARCHAR(160) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    student_id VARCHAR(80),
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    account_status VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP WITH TIME ZONE
);

CREATE TABLE authentication_identities (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255),
    password_hash VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_auth_identity_provider_subject UNIQUE(provider, provider_subject)
);

CREATE TABLE roles (
    name VARCHAR(20) PRIMARY KEY
);

INSERT INTO roles(name) VALUES ('USER'), ('ADMIN'), ('OWNER');

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_name VARCHAR(20) NOT NULL REFERENCES roles(name),
    PRIMARY KEY(user_id, role_name)
);

CREATE TABLE email_verifications (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_email_verification_user ON email_verifications(user_id, sent_at);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    access_token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    device_label VARCHAR(160)
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE login_attempts (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    attempted_email_hash VARCHAR(64) NOT NULL,
    successful BOOLEAN NOT NULL,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL,
    remote_address_hash VARCHAR(64)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    actor_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(80) NOT NULL,
    target_user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    outcome VARCHAR(20) NOT NULL,
    reason VARCHAR(500)
);

CREATE TABLE application_settings (
    setting_key VARCHAR(120) PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE schema_migrations (
    migration_key VARCHAR(120) PRIMARY KEY,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    details VARCHAR(500)
);

INSERT INTO schema_migrations(migration_key, applied_at, details)
VALUES ('authentication-foundation-v1', CURRENT_TIMESTAMP,
        'Provider-neutral identity and verification schema');
