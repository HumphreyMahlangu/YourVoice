-- One RSA blind-signature key per contest. The private key is encrypted with AES-256-GCM under a key derived from
-- the vote-credential pepper; the contest id is the GCM associated data, so keys cannot be swapped between contests.
CREATE TABLE contest_signing_keys (
    id UUID PRIMARY KEY,
    contest_id UUID NOT NULL,
    modulus VARCHAR(2048) NOT NULL,
    public_exponent VARCHAR(16) NOT NULL,
    encrypted_private_key VARCHAR(8192) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_contest_signing_keys_contest UNIQUE (contest_id),
    CONSTRAINT fk_contest_signing_keys_contest FOREIGN KEY (contest_id) REFERENCES contests (id)
);

-- Hash of the blinded token that was signed for this voting right. A blinded token is statistically independent of
-- the unblinded ballot token, so storing it cannot link a voter to a ballot. It lets the voter safely retry
-- issuance with the same blinded token after a lost response.
ALTER TABLE voting_rights
    ADD COLUMN blinded_token_hash VARCHAR(64);
