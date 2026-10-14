package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.AuthResponse;
import io.github.humphreymahlangu.votetrust.dto.LoginRequest;
import io.github.humphreymahlangu.votetrust.dto.RegisterRequest;
import io.github.humphreymahlangu.votetrust.entity.AccountRole;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditOutcome;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.InvalidCredentialsException;
import io.github.humphreymahlangu.votetrust.exception.LoginThrottledException;
import io.github.humphreymahlangu.votetrust.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.security.IdentityHashService;
import io.github.humphreymahlangu.votetrust.security.LoginThrottleService;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    // One message for both duplicate cases, so the response does not say whether the email or the ID number
    // already has an account. (Uniqueness checks remain an oracle in principle; rate limits and audit events
    // limit how much an attacker can learn from them.)
    static final String DUPLICATE_ACCOUNT_MESSAGE =
            "An account cannot be created with these details. If you already have an account, sign in instead.";

    private final UserAccountRepository userAccountRepository;
    private final VoterProfileRepository voterProfileRepository;
    private final VotingDistrictRepository votingDistrictRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final LoginThrottleService loginThrottleService;
    private final SecurityAuditService securityAuditService;
    private final SouthAfricanIdNumberValidator idNumberValidator;
    private final IdentityHashService identityHashService;
    // Compared against when the email is unknown, so every failed login costs one BCrypt verification and
    // response timing does not reveal whether an account exists.
    private final String timingEqualizationHash;

    public AuthService(
            UserAccountRepository userAccountRepository,
            VoterProfileRepository voterProfileRepository,
            VotingDistrictRepository votingDistrictRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            LoginThrottleService loginThrottleService,
            SecurityAuditService securityAuditService,
            SouthAfricanIdNumberValidator idNumberValidator,
            IdentityHashService identityHashService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.voterProfileRepository = voterProfileRepository;
        this.votingDistrictRepository = votingDistrictRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.loginThrottleService = loginThrottleService;
        this.securityAuditService = securityAuditService;
        this.idNumberValidator = idNumberValidator;
        this.identityHashService = identityHashService;
        this.timingEqualizationHash = passwordEncoder.encode("votetrust-timing-equalization-" + UUID.randomUUID());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        return register(request, SecurityAuditMetadata.system());
    }

    @Transactional
    public AuthResponse register(RegisterRequest request, SecurityAuditMetadata metadata) {
        String email = normalizeEmail(request.email());
        String fullName = normalizeFullName(request.fullName());

        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            securityAuditService.record(
                    SecurityAuditEventType.USER_REGISTER,
                    SecurityAuditOutcome.FAILURE,
                    null,
                    email,
                    metadata,
                    "Duplicate email rejected"
            );
            throw new DuplicateResourceException(DUPLICATE_ACCOUNT_MESSAGE);
        }

        SouthAfricanIdNumberValidator.ValidatedSouthAfricanId validatedId =
                idNumberValidator.validateForVoterRegistration(request.southAfricanIdNumber());
        String idNumberHash = identityHashService.hashSouthAfricanIdNumber(validatedId.normalizedIdNumber());

        if (voterProfileRepository.existsByIdNumberHash(idNumberHash)) {
            securityAuditService.record(
                    SecurityAuditEventType.USER_REGISTER,
                    SecurityAuditOutcome.FAILURE,
                    null,
                    email,
                    metadata,
                    "Duplicate voter identity rejected"
            );
            throw new DuplicateResourceException(DUPLICATE_ACCOUNT_MESSAGE);
        }

        VotingDistrict votingDistrict = votingDistrictRepository.findById(request.votingDistrictId())
                .orElseThrow(() -> new ResourceNotFoundException("Voting district not found"));

        UserAccount userAccount = new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                AccountRole.VOTER,
                true
        );

        UserAccount savedAccount = userAccountRepository.save(userAccount);
        voterProfileRepository.save(new VoterProfile(
                savedAccount,
                fullName,
                idNumberHash,
                request.idDocumentType(),
                validatedId.dateOfBirth(),
                votingDistrict
        ));
        AuthResponse response = authTokenService.issueTokens(savedAccount);
        securityAuditService.record(
                SecurityAuditEventType.USER_REGISTER,
                SecurityAuditOutcome.SUCCESS,
                savedAccount,
                metadata,
                "Voter account registered"
        );
        return response;
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        return login(request, SecurityAuditMetadata.system());
    }

    @Transactional
    public AuthResponse login(LoginRequest request, SecurityAuditMetadata metadata) {
        String email = normalizeEmail(request.email());

        Optional<Integer> lockedForSeconds = loginThrottleService.lockedForSeconds(email);
        if (lockedForSeconds.isPresent()) {
            securityAuditService.record(
                    SecurityAuditEventType.USER_LOGIN,
                    SecurityAuditOutcome.BLOCKED,
                    null,
                    email,
                    metadata,
                    "Sign-in temporarily locked after repeated failures"
            );
            throw new LoginThrottledException(lockedForSeconds.get());
        }

        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (userAccount == null) {
            passwordEncoder.matches(request.password(), timingEqualizationHash);
            throw failLogin(email, null, metadata, "Account not found");
        }

        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            throw failLogin(email, userAccount, metadata, "Invalid password");
        }

        if (!userAccount.isEnabled()) {
            throw failLogin(email, userAccount, metadata, "Account disabled");
        }

        loginThrottleService.recordSuccess(email);
        AuthResponse response = authTokenService.issueTokens(userAccount);
        securityAuditService.record(
                SecurityAuditEventType.USER_LOGIN,
                SecurityAuditOutcome.SUCCESS,
                userAccount,
                metadata,
                "JWT issued"
        );
        return response;
    }

    private InvalidCredentialsException failLogin(
            String email,
            UserAccount userAccount,
            SecurityAuditMetadata metadata,
            String detail
    ) {
        loginThrottleService.recordFailure(email);
        if (userAccount == null) {
            securityAuditService.record(SecurityAuditEventType.USER_LOGIN, SecurityAuditOutcome.FAILURE, null, email, metadata, detail);
        } else {
            securityAuditService.record(SecurityAuditEventType.USER_LOGIN, SecurityAuditOutcome.FAILURE, userAccount, metadata, detail);
        }
        return new InvalidCredentialsException();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
