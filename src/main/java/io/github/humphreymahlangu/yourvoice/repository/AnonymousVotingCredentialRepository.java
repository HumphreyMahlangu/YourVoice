package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.AnonymousVotingCredential;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spent ballot tokens. A row is written only when a ballot is cast, and holds a keyed hash of the token, never
 * anything that identifies the voter.
 */
public interface AnonymousVotingCredentialRepository extends JpaRepository<AnonymousVotingCredential, UUID> {

    /**
     * Records a ballot token as spent. Returns 0 when the token was already spent; a concurrent spend of the same
     * token waits for the first transaction and then also returns 0.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into anonymous_voting_credentials (id, contest_id, credential_hash, expires_at, used)
            values (:id, :contestId, :credentialHash, :expiresAt, true)
            on conflict (credential_hash) do nothing
            """, nativeQuery = true)
    int insertSpentIfAbsent(
            @Param("id") UUID id,
            @Param("contestId") UUID contestId,
            @Param("credentialHash") String credentialHash,
            @Param("expiresAt") Instant expiresAt
    );
}
