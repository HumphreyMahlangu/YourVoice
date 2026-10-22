package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.ContestResponse;
import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOption;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContestService {

    private final ElectionRepository electionRepository;
    private final ContestRepository contestRepository;
    private final ContestOptionRepository contestOptionRepository;

    public ContestService(
            ElectionRepository electionRepository,
            ContestRepository contestRepository,
            ContestOptionRepository contestOptionRepository
    ) {
        this.electionRepository = electionRepository;
        this.contestRepository = contestRepository;
        this.contestOptionRepository = contestOptionRepository;
    }

    @Transactional(readOnly = true)
    public List<ContestResponse> listContests(UUID electionId, boolean administrator) {
        ElectionVisibility.requireVisible(
                electionRepository.findById(electionId)
                        .orElseThrow(() -> new ResourceNotFoundException("Election not found")),
                administrator
        );

        List<Contest> contests = contestRepository.findByElectionIdOrderByDisplayOrderAscNameAsc(electionId);
        // One query for every contest's options instead of one per contest; groupingBy keeps the query's order.
        Map<UUID, List<ContestOption>> optionsByContest = contestOptionRepository
                .findByContestIdInOrderByDisplayOrderAscNameAsc(contests.stream().map(Contest::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(option -> option.getContest().getId()));

        return contests.stream()
                .map(contest -> ContestResponse.from(contest, optionsByContest.getOrDefault(contest.getId(), List.of())))
                .toList();
    }
}
