package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.SecurityAuditEventResponse;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEvent;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditEventType;
import io.github.humphreymahlangu.votetrust.entity.SecurityAuditOutcome;
import io.github.humphreymahlangu.votetrust.entity.UserAccount;
import io.github.humphreymahlangu.votetrust.repository.SecurityAuditEventRepository;
import io.github.humphreymahlangu.votetrust.security.SecurityAuditMetadata;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditService {

    private static final int MAX_AUDIT_EVENTS = 100;

    private final SecurityAuditEventRepository securityAuditEventRepository;
    private final Clock clock;

    public SecurityAuditService(SecurityAuditEventRepository securityAuditEventRepository, Clock clock) {
        this.securityAuditEventRepository = securityAuditEventRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            SecurityAuditEventType eventType,
            SecurityAuditOutcome outcome,
            UserAccount principal,
            SecurityAuditMetadata metadata,
            String detail
    ) {
        UUID principalUserId = principal == null ? null : principal.getId();
        String principalEmail = principal == null ? null : principal.getEmail();
        record(eventType, outcome, principalUserId, principalEmail, metadata, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            SecurityAuditEventType eventType,
            SecurityAuditOutcome outcome,
            UUID principalUserId,
            String principalEmail,
            SecurityAuditMetadata metadata,
            String detail
    ) {
        SecurityAuditMetadata safeMetadata = metadata == null ? SecurityAuditMetadata.system() : metadata;
        securityAuditEventRepository.save(new SecurityAuditEvent(
                eventType,
                outcome,
                principalUserId,
                truncate(principalEmail, 320),
                truncate(safeMetadata.clientIp(), 64),
                truncate(safeMetadata.userAgent(), 512),
                truncate(detail, 512),
                Instant.now(clock)
        ));
    }

    @Transactional(readOnly = true)
    public List<SecurityAuditEventResponse> listLatest(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_AUDIT_EVENTS));
        return securityAuditEventRepository.findAllByOrderByOccurredAtDesc(PageRequest.of(0, safeLimit))
                .stream()
                .map(SecurityAuditEventResponse::from)
                .toList();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
