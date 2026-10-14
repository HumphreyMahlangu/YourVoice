package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.PublishedBallotEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublishedBallotEntryRepository extends JpaRepository<PublishedBallotEntry, UUID> {

    @EntityGraph(attributePaths = {"contestOption"})
    List<PublishedBallotEntry> findByContestIdOrderByPositionAsc(UUID contestId);
}
