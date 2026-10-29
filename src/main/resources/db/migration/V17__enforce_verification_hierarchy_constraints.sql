-- Protect the same hierarchy and decision rules enforced by the profile services.
ALTER TABLE election_registrations ADD CONSTRAINT fk_registration_verified_municipality_parent
    FOREIGN KEY(registered_municipality_id, registered_province_id) REFERENCES geographic_municipalities(id, province_id);
ALTER TABLE voter_verifications ADD CONSTRAINT ck_verification_established_identity
    CHECK(identity_status <> 'VERIFIED' OR (established AND source_reference IS NOT NULL));
ALTER TABLE voter_verifications ADD CONSTRAINT ck_verification_local_evidence
    CHECK(location_status <> 'VERIFIED' OR province_id IS NOT NULL);
ALTER TABLE simulation_voter_records ADD CONSTRAINT ck_simulation_local_evidence
    CHECK(location_status <> 'VERIFIED' OR province_id IS NOT NULL);
ALTER TABLE location_correction_requests ADD CONSTRAINT ck_correction_effective_after_review
    CHECK(effective_at IS NULL OR effective_at >= reviewed_at);
