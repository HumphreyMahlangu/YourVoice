package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.LocationCorrectionRequest;
import io.github.humphreymahlangu.yourvoice.entity.CorrectionStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface LocationCorrectionRequestRepository extends JpaRepository<LocationCorrectionRequest, UUID> {
    List<LocationCorrectionRequest> findByVoterProfileIdOrderByRequestedAtDesc(UUID profileId);
    Page<LocationCorrectionRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Optional<LocationCorrectionRequest> findFirstByVoterProfileIdAndStatus(UUID profileId, CorrectionStatus status);
    boolean existsByVoterProfileIdAndStatusIn(UUID profileId, Collection<CorrectionStatus> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LocationCorrectionRequest r where r.id = :id")
    Optional<LocationCorrectionRequest> findForUpdate(UUID id);
    @Modifying
    @Query("update LocationCorrectionRequest r set r.requestReason = '[redacted]', r.reviewReason = '[redacted]' where r.status in ('APPLIED','REJECTED') and r.reviewedAt < :cutoff")
    int redactClosedBefore(Instant cutoff);
}
