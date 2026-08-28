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
import io.github.humphreymahlangu.votetrust.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.repository.VoterProfileRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingDistrictRepository;
import io.github.humphreymahlangu.votetrust.security.IdentityHashService;
import io.github.humphreymahlangu.votetrust.security.JwtService;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final VoterProfileRepository voterProfileRepository;
    private final VotingDistrictRepository votingDistrictRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityAuditService securityAuditService;
    private final SouthAfricanIdNumberValidator idNumberValidator;
    private final IdentityHashService identityHashService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            VoterProfileRepository voterProfileRepository,
            VotingDistrictRepository votingDistrictRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SecurityAuditService securityAuditService,
            SouthAfricanIdNumberValidator idNumberValidator,
            IdentityHashService identityHashService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.voterProfileRepository = voterProfileRepository;
        this.votingDistrictRepository = votingDistrictRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityAuditService = securityAuditService;
        this.idNumberValidator = idNumberValidator;
        this.identityHashService = identityHashService;
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
            throw new DuplicateResourceException("A user account with this email already exists");
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
            throw new DuplicateResourceException("This South African ID number is already linked to another account");
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
        AuthResponse response = createAuthResponse(savedAccount);
        securityAuditService.record(
                SecurityAuditEventType.USER_REGISTER,
                SecurityAuditOutcome.SUCCESS,
                savedAccount,
                metadata,
                "Voter account registered"
        );
        return response;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        return login(request, SecurityAuditMetadata.system());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, SecurityAuditMetadata metadata) {
        String email = normalizeEmail(request.email());
        UserAccount userAccount = userAccountRepository.findByEmailIgnoreCase(email)
                .orElse(null);

        if (userAccount == null) {
            securityAuditService.record(
                    SecurityAuditEventType.USER_LOGIN,
                    SecurityAuditOutcome.FAILURE,
                    null,
                    email,
                    metadata,
                    "Account not found"
            );
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), userAccount.getPasswordHash())) {
            securityAuditService.record(
                    SecurityAuditEventType.USER_LOGIN,
                    SecurityAuditOutcome.FAILURE,
                    userAccount,
                    metadata,
                    "Invalid password"
            );
            throw new InvalidCredentialsException();
        }

        if (!userAccount.isEnabled()) {
            securityAuditService.record(
                    SecurityAuditEventType.USER_LOGIN,
                    SecurityAuditOutcome.FAILURE,
                    userAccount,
                    metadata,
                    "Account disabled"
            );
            throw new InvalidCredentialsException();
        }

        AuthResponse response = createAuthResponse(userAccount);
        securityAuditService.record(
                SecurityAuditEventType.USER_LOGIN,
                SecurityAuditOutcome.SUCCESS,
                userAccount,
                metadata,
                "JWT issued"
        );
        return response;
    }

    private AuthResponse createAuthResponse(UserAccount userAccount) {
        JwtService.TokenResult tokenResult = jwtService.generateAccessToken(userAccount);
        return new AuthResponse(
                tokenResult.token(),
                "Bearer",
                tokenResult.expiresAt(),
                userAccount.getId(),
                userAccount.getEmail(),
                userAccount.getRole().name()
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }
}
