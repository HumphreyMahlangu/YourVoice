package io.github.humphreymahlangu.yourvoice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.humphreymahlangu.yourvoice.entity.AccountRole;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleEvent;
import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleTrigger;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.ElectionType;
import io.github.humphreymahlangu.yourvoice.entity.UserAccount;
import io.github.humphreymahlangu.yourvoice.repository.ElectionLifecycleEventRepository;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.security.JwtService;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ObservabilityIntegrationTest extends PostgreSqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ElectionRepository electionRepository;

    @Autowired
    private ElectionLifecycleEventRepository lifecycleEventRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void errorResponsesCarryTheRequestIdThatIsAlsoReturnedAsAHeader() throws Exception {
        mockMvc.perform(get("/api/v1/elections/{electionId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(header().string(
                        "X-Request-Id",
                        matchesPattern("^[0-9a-f-]{36}$")))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void wellFormedIncomingRequestIdIsKeptAndUnsafeOnesAreReplaced() throws Exception {
        mockMvc.perform(get("/api/v1/elections/{electionId}", UUID.randomUUID())
                        .header("X-Request-Id", "edge-proxy-1234"))
                .andExpect(header().string("X-Request-Id", "edge-proxy-1234"))
                .andExpect(jsonPath("$.requestId").value("edge-proxy-1234"));

        mockMvc.perform(get("/api/v1/elections/{electionId}", UUID.randomUUID())
                        .header("X-Request-Id", "bad id\nwith newline"))
                .andExpect(header().string("X-Request-Id", matchesPattern("^[0-9a-f-]{36}$")));
    }

    @Test
    void frameworkErrorsKeepTheirStatusInsteadOfBecoming500() throws Exception {
        mockMvc.perform(get("/api/v1/elections/{electionId}/no-such-endpoint", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(delete("/api/v1/admin/security-audit-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(AccountRole.ADMIN)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
    }

    @Test
    void prometheusMetricsAreAvailableOnlyToAdministrators() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(AccountRole.VOTER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/prometheus")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(AccountRole.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("yourvoice_ballots_cast_total")))
                .andExpect(content().string(containsString("yourvoice_elections_stuck")));
    }

    @Test
    void stuckElectionGaugeCountsActiveElectionsWithAFailedTransition() {
        Election stuck = electionRepository.save(new Election(
                "Stuck Election",
                ElectionType.NATIONAL,
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-02-01T00:00:00Z"),
                Instant.parse("2026-03-01T00:00:00Z"),
                Instant.parse("2026-03-02T00:00:00Z"),
                ElectionStatus.DRAFT
        ));
        lifecycleEventRepository.save(new ElectionLifecycleEvent(
                stuck.getId(),
                ElectionStatus.DRAFT,
                ElectionStatus.REGISTRATION_OPEN,
                ElectionLifecycleTrigger.AUTOMATIC,
                ElectionLifecycleOutcome.FAILURE,
                null,
                null,
                "Election must have at least one contest before registration opens",
                Instant.parse("2026-01-01T00:00:05Z")
        ));

        assertThat(meterRegistry.get("yourvoice.elections.stuck").gauge().value()).isEqualTo(1.0);
    }

    private String token(AccountRole role) {
        UserAccount account = userAccountRepository.save(new UserAccount(
                role.name().toLowerCase() + "." + UUID.randomUUID() + "@example.com",
                "unused-password-hash",
                role,
                true
        ));
        return jwtService.generateAccessToken(account).token();
    }
}
