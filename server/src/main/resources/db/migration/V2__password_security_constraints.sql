ALTER TABLE authentication_identities
    ADD CONSTRAINT uq_auth_identity_user_provider
    UNIQUE (user_id, provider);

CREATE UNIQUE INDEX uq_password_reset_token_hash
    ON password_reset_tokens(token_hash);

CREATE INDEX idx_password_reset_user_created
    ON password_reset_tokens(user_id, created_at);

INSERT INTO schema_migrations(migration_key, applied_at, details)
VALUES ('password-security-v2', CURRENT_TIMESTAMP,
        'Password identity and reset-token integrity constraints');
