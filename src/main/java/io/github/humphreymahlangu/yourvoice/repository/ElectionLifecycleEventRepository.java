package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElectionLifecycleEventRepository extends JpaRepository<ElectionLifecycleEvent, UUID> {

    List<ElectionLifecycleEvent> findByElectionIdOrderByEventSequenceAsc(UUID electionId);

    Optional<ElectionLifecycleEvent> findTopByElectionIdOrderByEventSequenceDesc(UUID electionId);
}
