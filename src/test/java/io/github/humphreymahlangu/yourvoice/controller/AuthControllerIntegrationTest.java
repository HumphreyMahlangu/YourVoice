package io.github.humphreymahlangu.yourvoice.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.yourvoice.security.IdentityHashService;
import io.github.humphreymahlangu.yourvoice.support.DatabaseCleaner;
import io.github.humphreymahlangu.yourvoice.support.PostgreSqlTestContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthControllerIntegrationTest extends PostgreSqlTestContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private VoterProfileRepository voterProfileRepository;

    @Autowired
    private VotingDistrictRepository votingDistrictRepository;

    @Autowired
    private IdentityHashService identityHashService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        DatabaseCleaner.clean(jdbcTemplate);
    }

    @Test
    void registerLoginAndReadCurrentAccount() throws Exception {
        VotingDistrict district = createVotingDistrict("AUTH-001");
        String registerBody = registerBody(
                "  Integration   Voter  ",
                "integration.voter@example.com",
                "VeryStrongPassword1",
                "8001015000086",
                district
        );

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("integration.voter@example.com"))
                .andExpect(jsonPath("$.role").value("VOTER"))
                .andReturn();

        String registerToken = JsonPath.read(registerResult.getResponse().getContentAsString(), "$.accessToken");

        VoterProfile voterProfile = voterProfileRepository.findByUserAccountId(
                userAccountRepository.findByEmailIgnoreCase("integration.voter@example.com").orElseThrow().getId()
        ).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(voterProfile.getFullName()).isEqualTo("Integration Voter");
        org.assertj.core.api.Assertions.assertThat(voterProfile.getIdNumberHash())
                .isEqualTo(identityHashService.hashSouthAfricanIdNumber("8001015000086"))
                .doesNotContain("8001015000086");
        org.assertj.core.api.Assertions.assertThat(voterProfile.getVotingDistrict().getId()).isEqualTo(district.getId());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + registerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("integration.voter@example.com"))
                .andExpect(jsonPath("$.role").value("VOTER"))
                .andExpect(jsonPath("$.enabled").value(true));

        String loginBody = """
                {
                  "email": "INTEGRATION.VOTER@example.com",
                  "password": "VeryStrongPassword1"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("integration.voter@example.com"));
    }

    @Test
    void refreshRotatesTokensAndReuseOfARotatedTokenRevokesTheSession() throws Exception {
        String firstRefreshToken = registerAndReturnRefreshToken("refresh.rotation@example.com", "9001015000085", "AUTH-101");

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("refresh.rotation@example.com"))
                .andReturn();
        String secondRefreshToken = JsonPath.read(refreshed.getResponse().getContentAsString(), "$.refreshToken");
        org.assertj.core.api.Assertions.assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // Replaying the rotated token looks like theft: it fails and revokes the whole family.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(firstRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(secondRefreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        String refreshToken = registerAndReturnRefreshToken("logout.voter@example.com", "8506154000085", "AUTH-102");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void repeatedFailedLoginsLockTheEmailEvenWhenNoAccountExists() throws Exception {
        String body = """
                {
                  "email": "throttled.unknown@example.com",
                  "password": "WrongPassword1"
                }
                """;
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.message").value("Too many failed sign-in attempts. Try again in 15 minute(s)."));
    }

    @Test
    void currentAccountRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, startsWith(MediaType.APPLICATION_JSON_VALUE)))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    @Test
    void duplicateRegistrationReturnsConflict() throws Exception {
        VotingDistrict district = createVotingDistrict("AUTH-002");
        String registerBody = registerBody(
                "Duplicate Voter",
                "duplicate.voter@example.com",
                "VeryStrongPassword1",
                "9001015000085",
                district
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "An account cannot be created with these details. If you already have an account, sign in instead."));
    }

    @Test
    void weakRegistrationPasswordReturnsValidationError() throws Exception {
        VotingDistrict district = createVotingDistrict("AUTH-003");
        String registerBody = registerBody(
                "Weak Password Voter",
                "weak.password@example.com",
                "lowercaseonly",
                "0001015000084",
                district
        );

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.password").value(
                        "must contain at least one uppercase letter, one lowercase letter, and one digit"
                ));
    }

    @Test
    void duplicateIdentityReturnsConflictEvenWhenEmailDiffers() throws Exception {
        VotingDistrict district = createVotingDistrict("AUTH-004");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(
                                "First Identity Owner",
                                "identity.owner@example.com",
                                "VeryStrongPassword1",
                                "0801015000087",
                                district
                        )))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(
                                "Identity Impersonator",
                                "identity.impostor@example.com",
                                "VeryStrongPassword1",
                                "0801015000087",
                                district
                        )))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("An account cannot be created with these details. If you already have an account, sign in instead."));
    }

    @Test
    void underageVoterCannotCreateAccount() throws Exception {
        VotingDistrict district = createVotingDistrict("AUTH-005");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(
                                "Underage Voter",
                                "underage.voter@example.com",
                                "VeryStrongPassword1",
                                "1501015000082",
                                district
                        )))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Voters must be at least 16 years old to register"));
    }

    private String registerAndReturnRefreshToken(String email, String idNumber, String districtCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Refresh Voter", email, "VeryStrongPassword1", idNumber,
                                createVotingDistrict(districtCode))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshTokenExpiresAt").isNotEmpty())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
    }

    private String refreshBody(String refreshToken) {
        return """
                {
                  "refreshToken": "%s"
                }
                """.formatted(refreshToken);
    }

    private VotingDistrict createVotingDistrict(String code) {
        return votingDistrictRepository.save(new VotingDistrict(
                code,
                "Cape Town Test Station",
                "Western Cape",
                "City of Cape Town",
                1
        ));
    }

    private String registerBody(
            String fullName,
            String email,
            String password,
            String idNumber,
            VotingDistrict district
    ) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "password": "%s",
                  "southAfricanIdNumber": "%s",
                  "idDocumentType": "SMART_ID_CARD",
                  "votingDistrictId": "%s"
                }
                """.formatted(fullName, email, password, idNumber, district.getId());
    }
}
