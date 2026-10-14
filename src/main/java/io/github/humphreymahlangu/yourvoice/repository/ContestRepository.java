package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestRepository extends JpaRepository<Contest, UUID> {

    @EntityGraph(attributePaths = {"election"})
    List<Contest> findByElectionIdOrderByDisplayOrderAscNameAsc(UUID electionId);

    List<Contest> findByElectionId(UUID electionId);

    @EntityGraph(attributePaths = {"election"})
    Optional<Contest> findByIdAndElectionId(UUID id, UUID electionId);

    boolean existsByElectionIdAndNameIgnoreCase(UUID electionId, String name);

    boolean existsByElectionIdAndDisplayOrder(UUID electionId, Integer displayOrder);

    boolean existsByElectionIdAndStatus(UUID electionId, ContestStatus status);

    boolean existsByElectionIdAndStatusNot(UUID electionId, ContestStatus status);

    long countByElectionId(UUID electionId);

    boolean existsByElectionIdAndNameIgnoreCaseAndIdNot(UUID electionId, String name, UUID id);

    boolean existsByElectionIdAndDisplayOrderAndIdNot(UUID electionId, Integer displayOrder, UUID id);
}
