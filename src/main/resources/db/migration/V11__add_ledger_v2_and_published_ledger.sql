-- Version 2 ledger entries hash and store only the UTC recording day instead of the exact cast instant.
-- Existing entries keep version 1 so their chains still verify.
ALTER TABLE ballot_ledger_entries
    ADD COLUMN hash_version SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE ballot_ledger_entries
    ALTER COLUMN hash_version DROP DEFAULT;

ALTER TABLE ballot_ledger_entries
    ADD CONSTRAINT ck_ballot_ledger_entries_hash_version CHECK (hash_version IN (1, 2));

-- The published ledger is built once a contest closes. Ballots are shuffled before being chained, so its order
-- reveals nothing about the order in which votes were cast, and every hash input is public.
CREATE TABLE published_ballot_entries (
    id UUID PRIMARY KEY,
    contest_id UUID NOT NULL,
    contest_option_id UUID NOT NULL,
    position BIGINT NOT NULL,
    previous_hash VARCHAR(64) NOT NULL,
    current_hash VARCHAR(64) NOT NULL,
    nonce VARCHAR(96) NOT NULL,
    CONSTRAINT uk_published_ballot_entries_contest_position UNIQUE (contest_id, position),
    CONSTRAINT uk_published_ballot_entries_contest_hash UNIQUE (contest_id, current_hash),
    CONSTRAINT fk_published_ballot_entries_contest FOREIGN KEY (contest_id) REFERENCES contests (id),
    CONSTRAINT fk_published_ballot_entries_option FOREIGN KEY (contest_option_id) REFERENCES contest_options (id)
);

CREATE INDEX ix_published_ballot_entries_contest ON published_ballot_entries (contest_id);

ALTER TABLE ledger_states
    ADD COLUMN published_head_hash VARCHAR(64),
    ADD COLUMN published_entry_count BIGINT,
    ADD CONSTRAINT ck_ledger_states_publication CHECK (
        (published_head_hash IS NULL AND published_entry_count IS NULL)
        OR (published_head_hash IS NOT NULL AND published_entry_count IS NOT NULL)
    );
