-- No existing voter or registration is marked verified. Claimed geography remains separate.
ALTER TABLE voter_profiles ALTER COLUMN voting_district_id DROP NOT NULL;
CREATE TABLE geographic_provinces (
    id UUID PRIMARY KEY, name VARCHAR(80) NOT NULL,
    normalised_name VARCHAR(80) NOT NULL UNIQUE
);
CREATE TABLE geographic_municipalities (
    id UUID PRIMARY KEY, province_id UUID NOT NULL REFERENCES geographic_provinces(id),
    name VARCHAR(160) NOT NULL, normalised_name VARCHAR(160) NOT NULL,
    UNIQUE(province_id, normalised_name), UNIQUE(id, province_id)
);
INSERT INTO geographic_provinces(id,name,normalised_name)
SELECT gen_random_uuid(), min(trim(province)), lower(trim(province)) FROM voting_districts GROUP BY lower(trim(province));
INSERT INTO geographic_municipalities(id,province_id,name,normalised_name)
SELECT gen_random_uuid(), p.id, min(trim(d.municipality)), lower(trim(d.municipality))
FROM voting_districts d JOIN geographic_provinces p ON p.normalised_name = lower(trim(d.province))
GROUP BY p.id, lower(trim(d.municipality));
CREATE TABLE simulation_voter_records (
    id UUID PRIMARY KEY, record_sequence BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    reference VARCHAR(80) NOT NULL UNIQUE, id_number_hash VARCHAR(64) NOT NULL, full_name VARCHAR(160) NOT NULL,
    identity_status VARCHAR(32) NOT NULL CHECK(identity_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED')),
    location_status VARCHAR(32) NOT NULL CHECK(location_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED')),
    province_id UUID REFERENCES geographic_provinces(id), municipality_id UUID REFERENCES geographic_municipalities(id),
    district_id UUID REFERENCES voting_districts(id), reason VARCHAR(500) NOT NULL, recorded_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY(municipality_id,province_id) REFERENCES geographic_municipalities(id,province_id),
    CHECK(municipality_id IS NULL OR province_id IS NOT NULL), CHECK(district_id IS NULL OR municipality_id IS NOT NULL)
);
CREATE INDEX ix_simulation_records_identity ON simulation_voter_records(id_number_hash, record_sequence DESC);
CREATE TABLE voter_verifications (
    id UUID PRIMARY KEY, voter_profile_id UUID NOT NULL UNIQUE REFERENCES voter_profiles(id),
    identity_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED', location_status VARCHAR(32) NOT NULL DEFAULT 'UNVERIFIED',
    established BOOLEAN NOT NULL DEFAULT false, source_reference VARCHAR(80),
    province_id UUID REFERENCES geographic_provinces(id), municipality_id UUID REFERENCES geographic_municipalities(id),
    district_id UUID REFERENCES voting_districts(id), message VARCHAR(500) NOT NULL,
    checked_at TIMESTAMPTZ, effective_at TIMESTAMPTZ,
    CHECK(identity_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED','UNAVAILABLE')),
    CHECK(location_status IN ('UNVERIFIED','PENDING','VERIFIED','REJECTED','UNAVAILABLE')),
    FOREIGN KEY(municipality_id,province_id) REFERENCES geographic_municipalities(id,province_id),
    CHECK(municipality_id IS NULL OR province_id IS NOT NULL), CHECK(district_id IS NULL OR municipality_id IS NOT NULL)
);
CREATE TABLE location_correction_requests (
    id UUID PRIMARY KEY, voter_profile_id UUID NOT NULL REFERENCES voter_profiles(id),
    province_id UUID REFERENCES geographic_provinces(id), municipality_id UUID REFERENCES geographic_municipalities(id),
    district_id UUID REFERENCES voting_districts(id), request_reason VARCHAR(500) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK(status IN ('PENDING','APPROVED','APPLIED','REJECTED')),
    requested_at TIMESTAMPTZ NOT NULL, reviewed_at TIMESTAMPTZ, reviewed_by UUID REFERENCES user_accounts(id),
    review_reason VARCHAR(500), source_reference VARCHAR(80), effective_at TIMESTAMPTZ,
    FOREIGN KEY(municipality_id,province_id) REFERENCES geographic_municipalities(id,province_id),
    CHECK(municipality_id IS NULL OR province_id IS NOT NULL), CHECK(district_id IS NULL OR municipality_id IS NOT NULL),
    CHECK(status = 'PENDING' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_reason IS NOT NULL)),
    CHECK(status NOT IN ('APPROVED','APPLIED') OR (effective_at IS NOT NULL AND source_reference IS NOT NULL))
);
CREATE UNIQUE INDEX uk_one_open_correction ON location_correction_requests(voter_profile_id) WHERE status IN ('PENDING','APPROVED');
CREATE TABLE profile_verification_audit (
    id UUID PRIMARY KEY, voter_profile_id UUID REFERENCES voter_profiles(id), actor_user_id UUID NOT NULL REFERENCES user_accounts(id),
    correction_request_id UUID REFERENCES location_correction_requests(id), action VARCHAR(40) NOT NULL,
    source_reference VARCHAR(80), reason VARCHAR(500) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL, effective_at TIMESTAMPTZ
);
