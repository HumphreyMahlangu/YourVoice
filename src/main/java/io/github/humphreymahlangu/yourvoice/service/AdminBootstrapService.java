package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.AdminBootstrapRequest;
import io.github.humphreymahlangu.yourvoice.dto.AuthResponse;
import io.github.humphreymahlangu.yourvoice.entity.AccountRole;
import io.github.humphreymahlangu.yourvoice.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.yourvoice.entity.SecurityAuditOutcome;
import io.github.humphreymahlangu.yourvoice.entity.UserAccount;
import io.github.humphreymahlangu.yourvoice.exception.AdminBootstrapException;
import io.github.humphreymahlangu.yourvoice.exception.DuplicateResourceException;
import io.github.humphreymahlangu.yourvoice.exception.InvalidBootstrapTokenException;
import io.github.humphreymahlangu.yourvoice.repository.UserAccountRepository;
import io.github.humphreymahlangu.yourvoice.security.AdminBootstrapProperties;
import io.github.humphreymahlangu.yourvoice.security.SecurityAuditMetadata;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AdminBootstrapService {

    private final AdminBootstrapProperties adminBootstrapProperties;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final SecurityAuditService securityAuditService;

    public AdminBootstrapService(
            AdminBootstrapProperties adminBootstrapProperties,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenService authTokenService,
            SecurityAuditService securityAuditService
    ) {
        this.adminBootstrapProperties = adminBootstrapProperties;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenService = authTokenService;
        this.securityAuditService = securityAuditService;
    }

    @Transactional
    public AuthResponse bootstrapFirstAdmin(AdminBootstrapRequest request, String bootstrapToken) {
        return bootstrapFirstAdmin(request, bootstrapToken, SecurityAuditMetadata.system());
    }

    @Transactional
    public AuthResponse bootstrapFirstAdmin(
            AdminBootstrapRequest request,
            String bootstrapToken,
            SecurityAuditMetadata metadata
    ) {
        String email = normalizeEmail(request.email());

        if (!adminBootstrapProperties.enabled()) {
            auditBootstrapFailure(email, metadata, "Bootstrap disabled");
            throw new AdminBootstrapException("Admin bootstrap is disabled");
        }

        if (!tokenMatches(bootstrapToken)) {
            auditBootstrapFailure(email, metadata, "Invalid bootstrap token");
            throw new InvalidBootstrapTokenException();
        }

        if (userAccountRepository.existsByRole(AccountRole.ADMIN)) {
            auditBootstrapFailure(email, metadata, "Admin already exists");
            throw new DuplicateResourceException("An admin account already exists");
        }

        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            auditBootstrapFailure(email, metadata, "Email already exists");
            throw new DuplicateResourceException("A user account with this email already exists");
        }

        UserAccount adminAccount = userAccountRepository.save(new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                AccountRole.ADMIN,
                true
        ));

        AuthResponse response = authTokenService.issueTokens(adminAccount);
        securityAuditService.record(
                SecurityAuditEventType.ADMIN_BOOTSTRAP,
                SecurityAuditOutcome.SUCCESS,
                adminAccount,
                metadata,
                "First admin created"
        );
        return response;
    }

    private void auditBootstrapFailure(String email, SecurityAuditMetadata metadata, String detail) {
        securityAuditService.record(
                SecurityAuditEventType.ADMIN_BOOTSTRAP,
                SecurityAuditOutcome.FAILURE,
                null,
                email,
                metadata,
                detail
        );
    }

    private boolean tokenMatches(String bootstrapToken) {
        if (!StringUtils.hasText(bootstrapToken) || !StringUtils.hasText(adminBootstrapProperties.token())) {
            return false;
        }

        byte[] expected = adminBootstrapProperties.token().getBytes(StandardCharsets.UTF_8);
        byte[] provided = bootstrapToken.getBytes(StandardCharsets.UTF_8);
        return expected.length == provided.length && MessageDigest.isEqual(expected, provided);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
