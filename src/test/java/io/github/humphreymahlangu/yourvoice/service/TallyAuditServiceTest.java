package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.github.humphreymahlangu.votetrust.dto.ContestOptionTallyRow;
import io.github.humphreymahlangu.votetrust.dto.ContestResultResponse;
import io.github.humphreymahlangu.votetrust.dto.DistrictRegistrationCount;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.ContestType;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.ElectionType;
import io.github.humphreymahlangu.votetrust.entity.RegistrationStatus;
import io.github.humphreymahlangu.votetrust.entity.LedgerState;
import io.github.humphreymahlangu.votetrust.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.PublishedBallotEntryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TallyAuditServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-07T22:00:00Z");
    private static final String PUBLISHED_HEAD = "ab".repeat(32);

    @Mock
    private ContestRepository contestRepository;

    @Mock
    private BallotLedgerEntryRepository ballotLedgerEntryRepository;

    @Mock
    private PublishedBallotEntryRepository publishedBallotEntryRepository;

    @Mock
    private ElectionRegistrationRepository electionRegistrationRepository;

    @Mock
    private VoteHashChainService voteHashChainService;

    @Mock
    private LedgerPublicationService ledgerPublicationService;

    private TallyAuditService tallyAuditService;

    @BeforeEach
    void setUp() {
        tallyAuditService = new TallyAuditService(
                contestRepository,
                ballotLedgerEntryRepository,
                publishedBallotEntryRepository,
                electionRegistrationRepository,
                voteHashChainService,
                ledgerPublicationService,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void finalResultsSeparateBlankAndSpoiltBallotsFromValidVotes() {
        UUID electionId = UUID.randomUUID();
        UUID contestId = UUID.randomUUID();
        Contest contest = closedContest();
        ContestOptionTallyRow party = tallyRow("Ubuntu Civic Party", ContestOptionType.PARTY, 1, 2);
        ContestOptionTallyRow candidate = tallyRow("Independent Candidate", ContestOptionType.INDEPENDENT_CANDIDATE, 2, 1);
        ContestOptionTallyRow blank = tallyRow("Blank ballot", ContestOptionType.BLANK_BALLOT, 98, 1);
        ContestOptionTallyRow spoilt = tallyRow("Spoilt ballot", ContestOptionType.SPOILT_BALLOT, 99, 1);

        when(contestRepository.findByIdAndElectionId(contestId, electionId)).thenReturn(Optional.of(contest));
        when(ballotLedgerEntryRepository.tallyContestOptions(contestId))
                .thenReturn(List.of(party, candidate, blank, spoilt));
        when(ballotLedgerEntryRepository.countByContestId(contestId)).thenReturn(5L);
        when(electionRegistrationRepository.countByDistrict(any(), eq(RegistrationStatus.ACTIVE)))
                .thenReturn(List.of(new DistrictRegistrationCount("Western Cape", "City of Cape Town", 1, 5L)));
        when(ledgerPublicationService.ensurePublished(contest)).thenReturn(publishedLedgerState(contest));

        ContestResultResponse response = tallyAuditService.getContestResult(electionId, contestId);

        assertThat(response.ballotsCast()).isEqualTo(5);
        assertThat(response.validVotes()).isEqualTo(3);
        assertThat(response.blankBallots()).isEqualTo(1);
        assertThat(response.spoiltBallots()).isEqualTo(1);
        assertThat(response.ledgerHeadHash()).isEqualTo(PUBLISHED_HEAD);
        assertThat(response.options())
                .extracting(option -> option.optionType().name())
                .containsExactly("PARTY", "INDEPENDENT_CANDIDATE");
        assertThat(response.options().getFirst().voteCount()).isEqualTo(2);
        assertThat(response.options().getFirst().leading()).isTrue();
    }

    @Test
    void turnoutCountsOnlyRegisteredVotersEligibleForTheContest() {
        UUID electionId = UUID.randomUUID();
        UUID contestId = UUID.randomUUID();
        Contest contest = closedContest();

        when(contestRepository.findByIdAndElectionId(contestId, electionId)).thenReturn(Optional.of(contest));
        when(ballotLedgerEntryRepository.tallyContestOptions(contestId)).thenReturn(List.of(
                tallyRow("Ubuntu Civic Party", ContestOptionType.PARTY, 1, 2)
        ));
        when(ballotLedgerEntryRepository.countByContestId(contestId)).thenReturn(2L);
        when(electionRegistrationRepository.countByDistrict(any(), eq(RegistrationStatus.ACTIVE))).thenReturn(List.of(
                new DistrictRegistrationCount(" western cape ", "City of Cape Town", 1, 4L),
                new DistrictRegistrationCount("Western Cape", "City of Cape Town", 2, 50L),
                new DistrictRegistrationCount("Gauteng", "City of Johannesburg", 1, 70L)
        ));
        when(ledgerPublicationService.ensurePublished(contest)).thenReturn(publishedLedgerState(contest));

        ContestResultResponse response = tallyAuditService.getContestResult(electionId, contestId);

        assertThat(response.registeredVoterCount()).isEqualTo(4);
        assertThat(response.turnoutPercentage()).isEqualByComparingTo("50.00");
    }

    private LedgerState publishedLedgerState(Contest contest) {
        LedgerState ledgerState = new LedgerState(contest);
        ledgerState.markPublished(PUBLISHED_HEAD, 5);
        return ledgerState;
    }

    private Contest closedContest() {
        Election election = new Election(
                "2026 Local Government Election",
                ElectionType.MUNICIPAL,
                Instant.parse("2026-07-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-07T07:00:00Z"),
                Instant.parse("2026-08-07T21:00:00Z"),
                ElectionStatus.COMPLETED
        );
        return new Contest(
                election,
                "Municipal Ward Councillor",
                ContestType.MUNICIPAL_WARD,
                ContestStatus.CLOSED,
                1,
                "Western Cape",
                "City of Cape Town",
                1
        );
    }

    private ContestOptionTallyRow tallyRow(
            String name,
            ContestOptionType optionType,
            Integer displayOrder,
            long voteCount
    ) {
        return new ContestOptionTallyRow(UUID.randomUUID(), name, optionType, displayOrder, voteCount);
    }
}
