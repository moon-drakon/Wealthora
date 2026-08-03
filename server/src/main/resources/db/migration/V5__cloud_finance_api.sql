ALTER TABLE accounts
    ADD COLUMN external_id VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE accounts
    ADD COLUMN opening_balance NUMERIC(19, 4) NOT NULL DEFAULT 0;
ALTER TABLE accounts
    ADD COLUMN icon_name VARCHAR(30) NOT NULL DEFAULT 'wallet';
ALTER TABLE accounts
    ADD COLUMN color_hex VARCHAR(7) NOT NULL DEFAULT '#1F7E60';
ALTER TABLE accounts
    ADD COLUMN institution_name VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE accounts
    ADD COLUMN opened_on DATE;
ALTER TABLE accounts
    ADD COLUMN default_account BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE accounts
SET external_id = 'ACCOUNT_'
        || UPPER(REPLACE(CAST(id AS VARCHAR), '-', '')),
    opening_balance = current_balance
WHERE external_id = '';

ALTER TABLE accounts
    ADD CONSTRAINT uq_accounts_user_external UNIQUE(user_id, external_id);

ALTER TABLE categories
    ADD COLUMN external_id VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE categories
    ADD COLUMN built_in BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories
    ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE categories
    ADD COLUMN parent_id UUID;

UPDATE categories
SET external_id = 'CUSTOM_'
        || UPPER(REPLACE(CAST(id AS VARCHAR), '-', ''))
WHERE external_id = '';

ALTER TABLE categories
    ADD CONSTRAINT uq_categories_user_external
    UNIQUE(user_id, external_id);
ALTER TABLE categories
    ADD CONSTRAINT fk_categories_owned_parent
    FOREIGN KEY(user_id, parent_id)
    REFERENCES categories(user_id, id) ON DELETE CASCADE;

ALTER TABLE transactions DROP CONSTRAINT fk_transactions_owned_account;
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_owned_account
    FOREIGN KEY(user_id, account_id)
    REFERENCES accounts(user_id, id) ON DELETE CASCADE;
ALTER TABLE transactions DROP CONSTRAINT fk_transactions_owned_category;
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_owned_category
    FOREIGN KEY(user_id, category_id)
    REFERENCES categories(user_id, id) ON DELETE CASCADE;

CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    source_account_id UUID NOT NULL,
    destination_account_id UUID NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    occurred_on DATE NOT NULL,
    tags VARCHAR(1000) NOT NULL DEFAULT '',
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_transfers_user_external UNIQUE(user_id, external_id),
    CONSTRAINT uq_transfers_user_id_id UNIQUE(user_id, id),
    CONSTRAINT fk_transfers_owned_source
        FOREIGN KEY(user_id, source_account_id)
        REFERENCES accounts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_transfers_owned_destination
        FOREIGN KEY(user_id, destination_account_id)
        REFERENCES accounts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_transfer_accounts_different
        CHECK(source_account_id <> destination_account_id),
    CONSTRAINT ck_transfer_amount_positive CHECK(amount > 0)
);

CREATE INDEX idx_transfers_user_date
    ON transfers(user_id, occurred_on);

ALTER TABLE transactions
    ADD COLUMN external_id VARCHAR(120) NOT NULL DEFAULT '';
ALTER TABLE transactions
    ADD COLUMN description VARCHAR(160) NOT NULL DEFAULT '';
ALTER TABLE transactions
    ADD COLUMN occurred_on DATE;
ALTER TABLE transactions
    ADD COLUMN payment_method VARCHAR(40) NOT NULL DEFAULT 'UNSPECIFIED';
ALTER TABLE transactions
    ADD COLUMN tags VARCHAR(1000) NOT NULL DEFAULT '';
ALTER TABLE transactions
    ADD COLUMN transfer_id UUID;
ALTER TABLE transactions
    ADD COLUMN transfer_direction VARCHAR(20);

UPDATE transactions
SET external_id = 'TRANSACTION_'
        || UPPER(REPLACE(CAST(id AS VARCHAR), '-', '')),
    occurred_on = CAST(occurred_at AS DATE)
WHERE external_id = '';

ALTER TABLE transactions
    ALTER COLUMN occurred_on SET NOT NULL;
ALTER TABLE transactions
    ADD CONSTRAINT uq_transactions_user_external
    UNIQUE(user_id, external_id);
ALTER TABLE transactions
    ADD CONSTRAINT fk_transactions_transfer
    FOREIGN KEY(transfer_id) REFERENCES transfers(id) ON DELETE CASCADE;
ALTER TABLE transactions
    ADD CONSTRAINT ck_transaction_amount_positive CHECK(amount > 0);

CREATE TABLE finance_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    default_account_id UUID NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_preferences_owned_account
        FOREIGN KEY(user_id, default_account_id)
        REFERENCES accounts(user_id, id) ON DELETE CASCADE
);

CREATE TABLE monthly_budgets (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    budget_month VARCHAR(7) NOT NULL,
    overall_limit NUMERIC(19, 4),
    category_limits VARCHAR(8000) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_monthly_budgets_user_month UNIQUE(user_id, budget_month)
);

CREATE TABLE budget_plans (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    overall_limit NUMERIC(19, 4),
    category_limits VARCHAR(8000) NOT NULL DEFAULT '',
    rollover_mode VARCHAR(40) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_budget_plans_user_external UNIQUE(user_id, external_id),
    CONSTRAINT ck_budget_plan_dates CHECK(end_date >= start_date)
);

CREATE INDEX idx_budget_plans_user_dates
    ON budget_plans(user_id, start_date, end_date);

CREATE TABLE recurring_entries (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entry_type VARCHAR(40) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    description VARCHAR(160) NOT NULL,
    category_id UUID,
    source_account_id UUID NOT NULL,
    destination_account_id UUID,
    frequency VARCHAR(40) NOT NULL,
    recurrence_interval INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_due_date DATE NOT NULL,
    recurring_kind VARCHAR(40) NOT NULL,
    reminder_days INTEGER NOT NULL DEFAULT 3,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_recurring_user_external UNIQUE(user_id, external_id),
    CONSTRAINT fk_recurring_owned_category
        FOREIGN KEY(user_id, category_id)
        REFERENCES categories(user_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_owned_source
        FOREIGN KEY(user_id, source_account_id)
        REFERENCES accounts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_recurring_owned_destination
        FOREIGN KEY(user_id, destination_account_id)
        REFERENCES accounts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_recurring_amount_positive CHECK(amount > 0),
    CONSTRAINT ck_recurring_interval_positive CHECK(recurrence_interval > 0),
    CONSTRAINT ck_recurring_dates
        CHECK(end_date IS NULL OR end_date >= start_date)
);

CREATE INDEX idx_recurring_user_due
    ON recurring_entries(user_id, active, next_due_date);

CREATE TABLE savings_goals (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    target_amount NUMERIC(19, 4) NOT NULL,
    target_date DATE NOT NULL,
    linked_account_id UUID NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_goals_user_external UNIQUE(user_id, external_id),
    CONSTRAINT uq_goals_user_id_id UNIQUE(user_id, id),
    CONSTRAINT fk_goals_owned_account
        FOREIGN KEY(user_id, linked_account_id)
        REFERENCES accounts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_amount_positive CHECK(target_amount > 0)
);

CREATE TABLE goal_contributions (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    goal_id UUID NOT NULL,
    contribution_date DATE NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_contributions_user_external UNIQUE(user_id, external_id),
    CONSTRAINT fk_contributions_owned_goal
        FOREIGN KEY(user_id, goal_id)
        REFERENCES savings_goals(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_contribution_amount_positive CHECK(amount > 0)
);

CREATE INDEX idx_contributions_user_goal_date
    ON goal_contributions(user_id, goal_id, contribution_date);

CREATE TABLE debts (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    direction VARCHAR(40) NOT NULL,
    counterparty VARCHAR(160) NOT NULL,
    original_amount NUMERIC(19, 4) NOT NULL,
    due_date DATE NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_debts_user_external UNIQUE(user_id, external_id),
    CONSTRAINT uq_debts_user_id_id UNIQUE(user_id, id),
    CONSTRAINT ck_debt_amount_positive CHECK(original_amount > 0)
);

CREATE TABLE debt_repayments (
    id UUID PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    debt_id UUID NOT NULL,
    repayment_date DATE NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    note VARCHAR(500) NOT NULL DEFAULT '',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_repayments_user_external UNIQUE(user_id, external_id),
    CONSTRAINT fk_repayments_owned_debt
        FOREIGN KEY(user_id, debt_id)
        REFERENCES debts(user_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_repayment_amount_positive CHECK(amount > 0)
);

CREATE INDEX idx_repayments_user_debt_date
    ON debt_repayments(user_id, debt_id, repayment_date);

INSERT INTO schema_migrations(migration_key, applied_at, details)
VALUES ('cloud-finance-api-v5', CURRENT_TIMESTAMP,
        'Owner-scoped cloud finance APIs, atomic ledger transfers, planning, and reports');
