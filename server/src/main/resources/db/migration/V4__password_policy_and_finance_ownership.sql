ALTER TABLE password_reset_tokens
    ADD COLUMN failed_attempts INTEGER NOT NULL DEFAULT 0;

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    account_type VARCHAR(40) NOT NULL,
    currency_code VARCHAR(3) NOT NULL,
    current_balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    archived BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_accounts_user_id_id UNIQUE(user_id, id)
);

CREATE INDEX idx_accounts_user ON accounts(user_id, archived);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(120) NOT NULL,
    category_type VARCHAR(40) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_categories_user_name_type
        UNIQUE(user_id, name, category_type),
    CONSTRAINT uq_categories_user_id_id UNIQUE(user_id, id)
);

CREATE INDEX idx_categories_user ON categories(user_id);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    account_id UUID NOT NULL,
    category_id UUID,
    transaction_type VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_transactions_owned_account
        FOREIGN KEY(user_id, account_id) REFERENCES accounts(user_id, id),
    CONSTRAINT fk_transactions_owned_category
        FOREIGN KEY(user_id, category_id) REFERENCES categories(user_id, id)
);

CREATE INDEX idx_transactions_user_date
    ON transactions(user_id, occurred_at);

INSERT INTO schema_migrations(migration_key, applied_at, details)
VALUES ('auth-finance-policy-v4', CURRENT_TIMESTAMP,
        'Eight-character password policy, reset attempt limit, and user-owned finance schema');
