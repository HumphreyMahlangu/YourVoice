ALTER TABLE voter_profiles
    ADD COLUMN full_name VARCHAR(160),
    ADD COLUMN id_document_type VARCHAR(40);

ALTER TABLE voter_profiles
    ADD CONSTRAINT ck_voter_profiles_id_document_type
        CHECK (
            id_document_type IS NULL
            OR id_document_type IN ('GREEN_BARCODED_ID', 'SMART_ID_CARD', 'TEMPORARY_ID_CERTIFICATE')
        );
