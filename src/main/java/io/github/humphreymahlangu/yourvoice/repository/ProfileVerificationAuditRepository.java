package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.ProfileVerificationAudit;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface ProfileVerificationAuditRepository extends JpaRepository<ProfileVerificationAudit, UUID> {
    Page<ProfileVerificationAudit> findAllByOrderByOccurredAtDesc(Pageable pageable);
    @Modifying
    @Query("update ProfileVerificationAudit a set a.reason = '[redacted]' where a.occurredAt < :cutoff")
    int redactBefore(Instant cutoff);
}
