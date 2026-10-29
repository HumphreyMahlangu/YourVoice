-- Keep old registrations, but do not certify self-reported geography or identity.
ALTER TABLE election_registrations ALTER COLUMN voting_district_id DROP NOT NULL;
ALTER TABLE election_registrations
    ADD COLUMN identity_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN location_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN source_reference VARCHAR(80),
    ADD COLUMN registered_province_id UUID REFERENCES geographic_provinces(id),
    ADD COLUMN registered_municipality_id UUID REFERENCES geographic_municipalities(id),
    ADD COLUMN registered_province VARCHAR(80),
    ADD COLUMN registered_municipality VARCHAR(160),
    ADD COLUMN registered_ward_number INTEGER,
    ADD CONSTRAINT ck_registration_verified_hierarchy CHECK (
        (registered_municipality IS NULL OR registered_province IS NOT NULL)
        AND (registered_ward_number IS NULL OR registered_municipality IS NOT NULL)
        AND (location_verified OR (registered_province IS NULL AND registered_municipality IS NULL AND registered_ward_number IS NULL))
    );

-- Explicit simulation election type: supports independently scoped contests without title inference.
ALTER TABLE elections DROP CONSTRAINT ck_elections_type;
ALTER TABLE elections ADD CONSTRAINT ck_elections_type CHECK(type IN ('NATIONAL','PROVINCIAL','MUNICIPAL','MIXED'));
ALTER TABLE election_registrations ADD CONSTRAINT ck_registration_trust CHECK (
    (NOT location_verified OR identity_verified)
    AND (NOT identity_verified OR source_reference IS NOT NULL)
    AND ((registered_province IS NULL) = (registered_province_id IS NULL))
    AND ((registered_municipality IS NULL) = (registered_municipality_id IS NULL))
    AND (registered_ward_number IS NULL OR registered_ward_number BETWEEN 1 AND 9999)
);
