package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.VotingRight;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VotingRightRepository extends JpaRepository<VotingRight, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<VotingRight> findByVoterProfileIdAndContestId(UUID voterProfileId, UUID contestId);

    /**
     * Creates the voting right unless it already exists, so two concurrent issuance requests for the same voter
     * both end up locking the same row instead of one failing on the unique constraint.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into voting_rights (id, voter_profile_id, contest_id, credential_issued)
            values (:id, :voterProfileId, :contestId, false)
            on conflict (voter_profile_id, contest_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("voterProfileId") UUID voterProfileId,
            @Param("contestId") UUID contestId
    );
}
