package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/**
 * RSA blind-signature key for one contest. Rows are inserted with ON CONFLICT DO NOTHING (see
 * ContestSigningKeyRepository) and never modified, so the entity is read-only.
 */
@Entity
@Table(
        name = "contest_signing_keys",
        uniqueConstraints = @UniqueConstraint(name = "uk_contest_signing_keys_contest", columnNames = "contest_id")
)
public class ContestSigningKey {

    @Id
    private UUID id;

    @Column(name = "contest_id", nullable = false, updatable = false)
    private UUID contestId;

    @Column(nullable = false, length = 2048, updatable = false)
    private String modulus;

    @Column(name = "public_exponent", nullable = false, length = 16, updatable = false)
    private String publicExponent;

    @Column(name = "encrypted_private_key", nullable = false, length = 8192, updatable = false)
    private String encryptedPrivateKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ContestSigningKey() {
    }

    public UUID getId() {
        return id;
    }

    public UUID getContestId() {
        return contestId;
    }

    public String getModulus() {
        return modulus;
    }

    public String getPublicExponent() {
        return publicExponent;
    }

    public String getEncryptedPrivateKey() {
        return encryptedPrivateKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
