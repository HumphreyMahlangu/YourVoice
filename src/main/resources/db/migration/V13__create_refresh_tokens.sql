-- Rotating refresh tokens. Only a SHA-256 hash of each random token is stored. Every rotation stays in the same
-- family, so reuse of an already-rotated token (a sign of theft) can revoke the whole family.
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_account_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user_account FOREIGN KEY (user_account_id) REFERENCES user_accounts (id)
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user_account ON refresh_tokens (user_account_id);
