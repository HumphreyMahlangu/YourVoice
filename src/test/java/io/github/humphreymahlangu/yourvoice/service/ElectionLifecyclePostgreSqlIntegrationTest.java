package io.github.humphreymahlangu.yourvoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOption;
import io.github.humphreymahlangu.yourvoice.entity.ContestOptionType;
import io.github.humphreymahlangu.yourvoice.entity.ContestStatus;
import io.github.humphreymahlangu.yourvoice.entity.ContestType;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionType;
import io.github.humphreymahlangu.yourvoice.repository.ContestOptionRepository;
import io.github.humphreymahlangu.yourvoice.repository.ContestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Import(ElectionLifecyclePostgreSqlIntegrationTest.FixedClockConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ElectionLifecyclePostgreSqlIntegrationTest extends PostgreSqlTestContainerSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    @Autowired
    private ElectionLifecycleService lifecycleService;

    @Autowired
    private ElectionRepository electionRepository;

    @Autowired
    private ContestRepository contestRepository;

    @Autowired
    private ContestOptionRepository contestOptionRepository;

    @Autowired
    private ElectionLifecycleEventRepository lifecycleEventRepository;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void persistsCatchUpTransitionsInDeterministicOrder() {
        Election election = electionRepository.save(new Election(
                "PostgreSQL Automatic Lifecycle",
                ElectionType.MUNICIPAL,
                NOW.minusSeconds(400),
                NOW.minusSeconds(300),
                NOW.minusSeconds(200),
                NOW.minusSeconds(100),
                ElectionStatus.DRAFT
        ));
        Contest contest = contestRepository.save(new Contest(
                election,
                "Ward 1 Councillor",
                ContestType.MUNICIPAL_WARD,
                ContestStatus.DRAFT,
                1,
                "Western Cape",
                "City of Cape Town",
                1
        ));
        contestOptionRepository.saveAll(List.of(
                new ContestOption(contest, "Ubuntu Civic Movement", ContestOptionType.PARTY, 1),
                new ContestOption(contest, "Independent Candidate", ContestOptionType.INDEPENDENT_CANDIDATE, 2)
        ));

        lifecycleService.advanceElection(election.getId());

        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.COMPLETED);
        assertThat(contestRepository.findById(contest.getId()).orElseThrow().getStatus())
                .isEqualTo(ContestStatus.CLOSED);
        assertThat(lifecycleEventRepository.findByElectionIdOrderByEventSequenceAsc(election.getId()))
                .extracting(event -> event.getNewStatus())
                .containsExactly(
                        ElectionStatus.REGISTRATION_OPEN,
                        ElectionStatus.REGISTRATION_CLOSED,
                        ElectionStatus.VOTING_OPEN,
                        ElectionStatus.COMPLETED
                );
    }

    @Test
    void storesOnlyOneFailureWhileInvalidElectionRemainsInSameStatus() {
        Election election = electionRepository.save(new Election(
                "Invalid Automatic Lifecycle",
                ElectionType.MUNICIPAL,
                NOW.minusSeconds(1),
                NOW.plusSeconds(100),
                NOW.plusSeconds(200),
                NOW.plusSeconds(300),
                ElectionStatus.DRAFT
        ));

        lifecycleService.advanceElection(election.getId());
        lifecycleService.advanceElection(election.getId());

        assertThat(electionRepository.findById(election.getId()).orElseThrow().getStatus())
                .isEqualTo(ElectionStatus.DRAFT);
        assertThat(lifecycleEventRepository.findByElectionIdOrderByEventSequenceAsc(election.getId()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getOutcome()).isEqualTo(ElectionLifecycleOutcome.FAILURE);
                    assertThat(event.getNewStatus()).isEqualTo(ElectionStatus.REGISTRATION_OPEN);
                });
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock lifecycleTestClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
