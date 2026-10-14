package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.humphreymahlangu.votetrust.dto.AuthResponse;
import io.github.humphreymahlangu.votetrust.dto.LoginRequest;
import io.github.humphreymahlangu.votetrust.dto.RegisterRequest;
import io.github.humphreymahlangu.votetrust.entity.AccountRole;
import io.github.humphreymahlangu.votetrust.entity.IdDocumentType;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.InvalidCredentialsException;
import io.github.humphreymahlangu.votetrust.exception.LoginThrottledException;
import io.github.humphreymahlangu.votetrust.observability.VoteTrustMetrics;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.security.IdentityHashService;
import io.github.humphreymahlangu.votetrust.security.LoginThrottleService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private VoterProfileRepository voterProfileRepository;

    @Mock
    private VotingDistrictRepository votingDistrictRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthTokenService authTokenService;

    @Mock
    private LoginThrottleService loginThrottleService;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private SouthAfricanIdNumberValidator idNumberValidator;

    @Mock
    private IdentityHashService identityHashService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userAccountRepository,
                voterProfileRepository,
                votingDistrictRepository,
                passwordEncoder,
                authTokenService,
                loginThrottleService,
                new VoteTrustMetrics(new SimpleMeterRegistry()),
                securityAuditService,
                idNumberValidator,
                identityHashService
        );
        // The constructor pre-computes the timing-equalization hash; tests only care about later calls.
        clearInvocations(passwordEncoder);
    }

    @Test
    void registerCreatesVoterAccountWithNormalizedEmailAndHashedPassword() throws Exception {
        UUID districtId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(
                "  New   Voter  ",
                " NewVoter@Example.COM ",
                "VeryStrongPassword",
                "8001015000086",
                IdDocumentType.SMART_ID_CARD,
                districtId
        );
        UUID userId = UUID.randomUUID();
        VotingDistrict votingDistrict = new VotingDistrict(
                "WC001-0001",
                "Cape Town Ward 1 Station",
                "Western Cape",
                "City of Cape Town",
                1
        );

        when(userAccountRepository.existsByEmailIgnoreCase("newvoter@example.com")).thenReturn(false);
        when(idNumberValidator.validateForVoterRegistration("8001015000086"))
                .thenReturn(new SouthAfricanIdNumberValidator.ValidatedSouthAfricanId(
                        "8001015000086",
                        LocalDate.of(1980, 1, 1)
                ));
        when(identityHashService.hashSouthAfricanIdNumber("8001015000086")).thenReturn("protected-id-hash");
        when(voterProfileRepository.existsByIdNumberHash("protected-id-hash")).thenReturn(false);
        when(votingDistrictRepository.findById(districtId)).thenReturn(Optional.of(votingDistrict));
        when(passwordEncoder.encode("VeryStrongPassword")).thenReturn("hashed-password");
        when(userAccountRepository.save(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount userAccount = invocation.getArgument(0);
            setId(userAccount, userId);
            return userAccount;
        });
        when(authTokenService.issueTokens(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount account = invocation.getArgument(0);
            return new AuthResponse(
                    "signed.jwt",
                    "Bearer",
                    Instant.parse("2026-08-07T10:15:00Z"),
                    account.getId(),
                    account.getEmail(),
                    account.getRole().name(),
                    "refresh-token",
                    Instant.parse("2026-08-07T22:00:00Z")
            );
        });
        when(voterProfileRepository.save(any(VoterProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuthResponse response = authService.register(request);

        ArgumentCaptor<UserAccount> accountCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).save(accountCaptor.capture());
        UserAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getEmail()).isEqualTo("newvoter@example.com");
        assertThat(savedAccount.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedAccount.getRole()).isEqualTo(AccountRole.VOTER);
        assertThat(savedAccount.isEnabled()).isTrue();

        ArgumentCaptor<VoterProfile> profileCaptor = ArgumentCaptor.forClass(VoterProfile.class);
        verify(voterProfileRepository).save(profileCaptor.capture());
        VoterProfile savedProfile = profileCaptor.getValue();
        assertThat(savedProfile.getFullName()).isEqualTo("New Voter");
        assertThat(savedProfile.getIdNumberHash()).isEqualTo("protected-id-hash");
        assertThat(savedProfile.getIdDocumentType()).isEqualTo(IdDocumentType.SMART_ID_CARD);
        assertThat(savedProfile.getDateOfBirth()).isEqualTo(LocalDate.of(1980, 1, 1));
        assertThat(savedProfile.getVotingDistrict()).isSameAs(votingDistrict);
        assertThat(response.accessToken()).isEqualTo("signed.jwt");
        assertThat(response.userId()).isEqualTo(userId);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest(
                "Duplicate Voter",
                "voter@example.com",
                "VeryStrongPassword",
                "8001015000086",
                IdDocumentType.SMART_ID_CARD,
                UUID.randomUUID()
        );
        when(userAccountRepository.existsByEmailIgnoreCase("voter@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage(AuthService.DUPLICATE_ACCOUNT_MESSAGE);

        verify(userAccountRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void loginRejectsInvalidPasswordWithGenericError() {
        LoginRequest request = new LoginRequest("voter@example.com", "WrongPassword");
        UserAccount userAccount = new UserAccount("voter@example.com", "hashed-password", AccountRole.VOTER, true);

        when(userAccountRepository.findByEmailIgnoreCase("voter@example.com")).thenReturn(Optional.of(userAccount));
        when(passwordEncoder.matches("WrongPassword", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(authTokenService, never()).issueTokens(any());
        verify(loginThrottleService).recordFailure("voter@example.com");
    }

    @Test
    void loginWithUnknownEmailStillVerifiesAPasswordSoTimingMatchesKnownAccounts() {
        LoginRequest request = new LoginRequest("nobody@example.com", "SomePassword1");
        when(userAccountRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");

        verify(passwordEncoder).matches(eq("SomePassword1"), any());
        verify(loginThrottleService).recordFailure("nobody@example.com");
    }

    @Test
    void lockedEmailIsRejectedBeforeTheAccountIsLookedUp() {
        LoginRequest request = new LoginRequest("Voter@Example.com", "VeryStrongPassword1");
        when(loginThrottleService.lockedForSeconds("voter@example.com")).thenReturn(Optional.of(600));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(LoginThrottledException.class)
                .hasMessage("Too many failed sign-in attempts. Try again in 10 minute(s).");

        verify(userAccountRepository, never()).findByEmailIgnoreCase(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    private void setId(UserAccount userAccount, UUID userId) throws Exception {
        Field idField = UserAccount.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(userAccount, userId);
    }
}
