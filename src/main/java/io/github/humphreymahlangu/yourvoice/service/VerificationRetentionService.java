package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.repository.LocationCorrectionRequestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ProfileVerificationAuditRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VerificationRetentionService {
    private final LocationCorrectionRequestRepository requests;
    private final ProfileVerificationAuditRepository audit;
    private final Clock clock;
    public VerificationRetentionService(LocationCorrectionRequestRepository requests, ProfileVerificationAuditRepository audit, Clock clock) {
        this.requests = requests; this.audit = audit; this.clock = clock;
    }
    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    @Transactional
    public void redactOldReasons() {
        Instant cutoff = Instant.now(clock).minus(90, ChronoUnit.DAYS);
        requests.redactClosedBefore(cutoff); audit.redactBefore(cutoff);
    }
}
