package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElectionRepository extends JpaRepository<Election, UUID> {

    List<Election> findAllByOrderByRegistrationStartAtDesc();

    @Query("select election.id from Election election where election.status in :statuses")
    List<UUID> findIdsByStatusIn(@Param("statuses") Collection<ElectionStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select election from Election election where election.id = :id")
    Optional<Election> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByNameIgnoreCase(String name);

    @Query("""
            select count(election)
            from Election election
            where election.status in :statuses
              and exists (
                  select 1
                  from ElectionLifecycleEvent event
                  where event.electionId = election.id
                    and event.previousStatus = election.status
                    and event.outcome = :failure
              )
            """)
    long countWithFailedTransitionFromCurrentStatus(
            @Param("statuses") Collection<ElectionStatus> statuses,
            @Param("failure") ElectionLifecycleOutcome failure
    );
}
