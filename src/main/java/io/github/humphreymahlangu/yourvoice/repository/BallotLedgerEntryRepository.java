package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.dto.ContestOptionTallyRow;
import io.github.humphreymahlangu.yourvoice.entity.BallotLedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BallotLedgerEntryRepository extends JpaRepository<BallotLedgerEntry, UUID> {

    @EntityGraph(attributePaths = {"contest", "contestOption"})
    List<BallotLedgerEntry> findByContestIdOrderByLedgerIndexAsc(UUID contestId);

    long countByContestId(UUID contestId);

    @Query("""
            select new io.github.humphreymahlangu.yourvoice.dto.ContestOptionTallyRow(
                contestOption.id,
                contestOption.name,
                contestOption.optionType,
                contestOption.displayOrder,
                count(ballotLedgerEntry.id)
            )
            from ContestOption contestOption
            left join BallotLedgerEntry ballotLedgerEntry on ballotLedgerEntry.contestOption = contestOption
            where contestOption.contest.id = :contestId
            group by contestOption.id, contestOption.name, contestOption.optionType, contestOption.displayOrder
            order by contestOption.displayOrder asc, contestOption.name asc
            """)
    List<ContestOptionTallyRow> tallyContestOptions(@Param("contestId") UUID contestId);
}
