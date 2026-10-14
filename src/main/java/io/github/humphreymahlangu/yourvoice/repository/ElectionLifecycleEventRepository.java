package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.ElectionLifecycleEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionLifecycleEventRepository extends JpaRepository<ElectionLifecycleEvent, UUID> {

    List<ElectionLifecycleEvent> findByElectionIdOrderByEventSequenceAsc(UUID electionId);

    Optional<ElectionLifecycleEvent> findTopByElectionIdOrderByEventSequenceDesc(UUID electionId);
}
