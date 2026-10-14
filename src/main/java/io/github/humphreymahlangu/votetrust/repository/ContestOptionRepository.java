package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContestOptionRepository extends JpaRepository<ContestOption, UUID> {

    List<ContestOption> findByContestIdOrderByDisplayOrderAscNameAsc(UUID contestId);

    List<ContestOption> findByContestIdInOrderByDisplayOrderAscNameAsc(Collection<UUID> contestIds);

    @EntityGraph(attributePaths = {"contest", "contest.election"})
    Optional<ContestOption> findByIdAndContestId(UUID id, UUID contestId);

    boolean existsByContestIdAndNameIgnoreCase(UUID contestId, String name);

    boolean existsByContestIdAndDisplayOrder(UUID contestId, Integer displayOrder);

    boolean existsByContestIdAndOptionType(UUID contestId, ContestOptionType optionType);

    long countByContestIdAndOptionTypeIn(UUID contestId, Collection<ContestOptionType> optionTypes);

    boolean existsByContestIdAndNameIgnoreCaseAndIdNot(UUID contestId, String name, UUID id);

    boolean existsByContestIdAndDisplayOrderAndIdNot(UUID contestId, Integer displayOrder, UUID id);

    boolean existsByContestIdAndOptionTypeAndIdNot(UUID contestId, ContestOptionType optionType, UUID id);

    List<ContestOption> findByContestId(UUID contestId);
}
