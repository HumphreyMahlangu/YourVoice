package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.AdminAccountResponse;
import io.github.humphreymahlangu.votetrust.dto.CreateAdminAccountRequest;
import io.github.humphreymahlangu.votetrust.entity.AccountRole;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditOutcome;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.repository.UserAccountRepository;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import io.github.humphreymahlangu.votetrust.security.UserPrincipal;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets an existing administrator create further administrators, so the one-time bootstrap is not the only way to
 * add an admin. Every creation is audited with the acting administrator.
 */
@Service
public class AdminAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService securityAuditService;

    public AdminAccountService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService securityAuditService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityAuditService = securityAuditService;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> listAdministrators() {
        return userAccountRepository.findByRoleOrderByCreatedAtAsc(AccountRole.ADMIN).stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminAccountResponse createAdministrator(
            CreateAdminAccountRequest request,
            UserPrincipal actor,
            SecurityAuditMetadata metadata
    ) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("A user account with this email already exists");
        }

        UserAccount administrator = userAccountRepository.save(new UserAccount(
                email,
                passwordEncoder.encode(request.password()),
                AccountRole.ADMIN,
                true
        ));
        securityAuditService.record(
                SecurityAuditEventType.ADMIN_ACCOUNT_CREATED,
                SecurityAuditOutcome.SUCCESS,
                actor.id(),
                actor.email(),
                metadata,
                "Administrator account created for " + email
        );
        return AdminAccountResponse.from(administrator);
    }
}
