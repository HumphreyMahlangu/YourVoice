package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.ContestSigningKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContestSigningKeyRepository extends JpaRepository<ContestSigningKey, UUID> {

    Optional<ContestSigningKey> findByContestId(UUID contestId);

    /**
     * Stores a contest's key unless one already exists. Two concurrent generators may both build a key pair, but
     * only the first insert wins and both callers then read that stored key.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into contest_signing_keys (id, contest_id, modulus, public_exponent, encrypted_private_key, created_at)
            values (:id, :contestId, :modulus, :publicExponent, :encryptedPrivateKey, :createdAt)
            on conflict (contest_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("contestId") UUID contestId,
            @Param("modulus") String modulus,
            @Param("publicExponent") String publicExponent,
            @Param("encryptedPrivateKey") String encryptedPrivateKey,
            @Param("createdAt") Instant createdAt
    );
}
