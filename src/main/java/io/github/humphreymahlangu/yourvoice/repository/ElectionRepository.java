package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElectionRepository extends JpaRepository<Election, UUID> {

    Page<Election> findAllByOrderByRegistrationStartAtDesc(Pageable pageable);

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
                    and event.eventSequence = (
                        select max(latest.eventSequence)
                        from ElectionLifecycleEvent latest
                        where latest.electionId = election.id
                    )
              )
            """)
    long countWithFailedTransitionFromCurrentStatus(
            @Param("statuses") Collection<ElectionStatus> statuses,
            @Param("failure") ElectionLifecycleOutcome failure
    );

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
