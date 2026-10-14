package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.LedgerState;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerStateRepository extends JpaRepository<LedgerState, UUID> {

    Optional<LedgerState> findByContestId(UUID contestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ledgerState
            from LedgerState ledgerState
            join fetch ledgerState.contest
            where ledgerState.contest.id = :contestId
            """)
    Optional<LedgerState> findByContestIdForUpdate(@Param("contestId") UUID contestId);

    /**
     * Creates the genesis ledger state for a contest unless it already exists. Using ON CONFLICT instead of
     * "select then insert" means concurrent first ballots never fail on the unique contest constraint.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert into ledger_states (id, contest_id, current_hash, next_ledger_index, updated_at)
            values (:id, :contestId, :genesisHash, 0, :now)
            on conflict (contest_id) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("contestId") UUID contestId,
            @Param("genesisHash") String genesisHash,
            @Param("now") Instant now
    );
}
