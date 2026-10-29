package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.CreateSimulationVoterRequest;
import io.github.humphreymahlangu.yourvoice.dto.LocationCorrectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ProfileVerificationResponse;
import io.github.humphreymahlangu.yourvoice.dto.RequestLocationCorrection;
import io.github.humphreymahlangu.yourvoice.dto.ReviewLocationCorrection;
import io.github.humphreymahlangu.yourvoice.entity.CorrectionStatus;
import io.github.humphreymahlangu.yourvoice.entity.LocationCorrectionRequest;
import io.github.humphreymahlangu.yourvoice.entity.ProfileVerificationAudit;
import io.github.humphreymahlangu.yourvoice.entity.RegisteredLocation;
import io.github.humphreymahlangu.yourvoice.entity.SimulationVoterRecord;
import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import io.github.humphreymahlangu.yourvoice.entity.VoterVerification;
import io.github.humphreymahlangu.yourvoice.exception.DuplicateResourceException;
import io.github.humphreymahlangu.yourvoice.exception.EligibilityException;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.exception.VerificationUnavailableException;
import io.github.humphreymahlangu.yourvoice.repository.LocationCorrectionRequestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ProfileVerificationAuditRepository;
import io.github.humphreymahlangu.yourvoice.repository.SimulationVoterRecordRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterProfileRepository;
import io.github.humphreymahlangu.yourvoice.repository.VoterVerificationRepository;
import io.github.humphreymahlangu.yourvoice.security.IdentityHashService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class VoterProfileVerificationService {
    private final VoterProfileRepository profiles;
    private final VoterVerificationRepository verifications;
    private final LocationCorrectionRequestRepository corrections;
    private final SimulationVoterRecordRepository records;
    private final ProfileVerificationAuditRepository audit;
    private final SimulationVerificationSource source;
    private final GeographicRecordService geography;
    private final IdentityHashService hashes;
    private final SouthAfricanIdNumberValidator ids;
    private final Clock clock;
    private final EntityManager entityManager;
    public VoterProfileVerificationService(VoterProfileRepository profiles, VoterVerificationRepository verifications,
            LocationCorrectionRequestRepository corrections, SimulationVoterRecordRepository records, ProfileVerificationAuditRepository audit,
            SimulationVerificationSource source, GeographicRecordService geography, IdentityHashService hashes,
            SouthAfricanIdNumberValidator ids, Clock clock, EntityManager entityManager) {
        this.profiles = profiles; this.verifications = verifications; this.corrections = corrections; this.records = records;
        this.audit = audit; this.source = source; this.geography = geography; this.hashes = hashes; this.ids = ids; this.clock = clock; this.entityManager = entityManager;
    }
    public boolean sourceAvailable() { return source.available(); }
    public void requireSource() { if (!source.available()) throw new VerificationUnavailableException(); }
    /** Global lock order: profile -> verification -> correction. Serialises registration, checking and review across instances. */
    @Transactional
    public VoterVerification lockState(UUID userId) {
        VoterProfile profile = profiles.findByUserAccountIdForUpdate(userId).orElseThrow(() -> new ResourceNotFoundException("Voter profile not found"));
        entityManager.refresh(profile);
        verifications.insertIfAbsent(UUID.randomUUID(), profile.getId());
        VoterVerification state = verifications.findForUpdate(profile.getId()).orElseThrow();
        entityManager.refresh(state);
        Optional<LocationCorrectionRequest> due = corrections.findFirstByVoterProfileIdAndStatus(profile.getId(), CorrectionStatus.APPROVED);
        if (due.isPresent() && !Instant.now(clock).isBefore(due.get().getEffectiveAt())) {
            LocationCorrectionRequest correction = corrections.findForUpdate(due.get().getId()).orElseThrow();
            entityManager.refresh(correction);
            state.recordOutcome(VerificationStatus.VERIFIED,
                    correction.getLocation().getProvince() == null ? VerificationStatus.UNVERIFIED : VerificationStatus.VERIFIED,
                    correction.getSourceReference(), correction.getLocation(), "Approved fictional area correction is effective. Existing registrations keep their saved area.", correction.getEffectiveAt());
            correction.markApplied();
            audit.save(new ProfileVerificationAudit(profile.getId(), correction.getReviewedBy(), correction.getId(), "CORRECTION_APPLIED", correction.getSourceReference(), "Approved correction became effective", Instant.now(clock), correction.getEffectiveAt()));
        }
        return state;
    }
    @Transactional
    public ProfileVerificationResponse profile(UUID userId) { return ProfileVerificationResponse.from(lockState(userId), source.available()); }
    @Transactional
    public ProfileVerificationResponse requestVerification(UUID userId) {
        VoterVerification state = lockState(userId); Instant now = Instant.now(clock);
        if (!source.available()) return ProfileVerificationResponse.from(state, false);
        VoterProfile profile = state.getVoterProfile();
        Optional<SimulationVoterRecord> match = source.find(profile.getIdNumberHash());
        if (match.isEmpty()) {
            state.recordOutcome(VerificationStatus.PENDING, VerificationStatus.PENDING, null, null,
                    "No matching fictional record is configured yet. Ask a simulation administrator to review your details, then retry.", now);
        } else {
            SimulationVoterRecord record = match.get();
            if (!record.getFullName().trim().equalsIgnoreCase(profile.getFullName() == null ? "" : profile.getFullName().trim())) {
                state.recordOutcome(VerificationStatus.REJECTED, VerificationStatus.UNVERIFIED, record.getReference(), null,
                        "Your details do not match the fictional source. Ask a simulation administrator to review the record.", now);
            } else {
                if (state.isEstablished() && record.getIdentityStatus() == VerificationStatus.VERIFIED
                        && !state.getLocation().sameAs(record.getLocation()))
                    throw new DuplicateResourceException("Request an area correction to change an established location. Existing election registrations will keep their saved area.");
                state.recordOutcome(record.getIdentityStatus(), record.getLocationStatus(), record.getReference(), record.getLocation(), record.getReason(), now);
            }
        }
        audit.save(new ProfileVerificationAudit(profile.getId(), userId, null, "VERIFICATION_CHECK", state.getSourceReference(),
                "Fictional source check: " + state.getIdentityStatus().name(), now, state.getEffectiveAt()));
        return ProfileVerificationResponse.from(state, true);
    }
    @Transactional
    public SimulationVoterRecord createRecord(UUID actor, CreateSimulationVoterRequest request) {
        if (!request.fictional()) throw new EligibilityException("Only fictional simulation records may be configured");
        if (request.identityStatus() == VerificationStatus.UNAVAILABLE || request.locationStatus() == VerificationStatus.UNAVAILABLE)
            throw new EligibilityException("Unavailable describes the source service, not a fictional record");
        if (records.existsByReference(request.reference())) throw new DuplicateResourceException("Fictional source reference already exists");
        RegisteredLocation location = geography.resolve(request.location());
        if (request.locationStatus() == VerificationStatus.VERIFIED && location.getProvince() == null)
            throw new EligibilityException("A verified local area requires a configured province; use unverified location for national-only identity records");
        if (request.locationStatus() != VerificationStatus.VERIFIED && location.getProvince() != null)
            throw new EligibilityException("Only verified fictional source locations may carry geographic attributes");
        String protectedIdentity = hashes.hashSouthAfricanIdNumber(ids.validateForVoterRegistration(request.southAfricanIdNumber()).normalizedIdNumber());
        SimulationVoterRecord saved = records.saveAndFlush(new SimulationVoterRecord(request.reference(), protectedIdentity, request.fullName().trim().replaceAll("\\s+", " "),
                request.identityStatus(), request.locationStatus(), location, request.reason().trim(), Instant.now(clock)));
        audit.save(new ProfileVerificationAudit(null, actor, null, "FICTIONAL_RECORD_CREATED", saved.getReference(), "Fictional source configured", Instant.now(clock), null));
        return saved;
    }
    @Transactional
    public LocationCorrectionResponse requestCorrection(UUID userId, RequestLocationCorrection request) {
        VoterVerification state = lockState(userId);
        if (!state.isEstablished()) throw new EligibilityException("Complete a fictional identity check before requesting an area correction");
        if (corrections.existsByVoterProfileIdAndStatusIn(state.getVoterProfile().getId(), List.of(CorrectionStatus.PENDING, CorrectionStatus.APPROVED)))
            throw new DuplicateResourceException("An area correction is already awaiting review or its effective date");
        RegisteredLocation area = geography.resolve(request.location());
        if (state.getLocation().sameAs(area)) throw new EligibilityException("Choose a different area for the correction request");
        LocationCorrectionRequest saved = corrections.save(new LocationCorrectionRequest(state.getVoterProfile(), area, request.reason().trim(), Instant.now(clock)));
        audit.save(new ProfileVerificationAudit(state.getVoterProfile().getId(), userId, saved.getId(), "CORRECTION_REQUESTED", null, "Area correction requested", Instant.now(clock), null));
        return LocationCorrectionResponse.from(saved);
    }
    @Transactional
    public List<LocationCorrectionResponse> myCorrections(UUID userId) {
        VoterVerification state = lockState(userId);
        return corrections.findByVoterProfileIdOrderByRequestedAtDesc(state.getVoterProfile().getId()).stream().map(LocationCorrectionResponse::from).toList();
    }
    @Transactional
    public LocationCorrectionResponse review(UUID actor, UUID requestId, ReviewLocationCorrection review) {
        LocationCorrectionRequest initial = corrections.findById(requestId).orElseThrow(() -> new ResourceNotFoundException("Correction request not found"));
        VoterVerification state = lockState(initial.getVoterProfile().getUserAccount().getId());
        LocationCorrectionRequest request = corrections.findForUpdate(requestId).orElseThrow(); entityManager.refresh(request);
        if (request.getStatus() != CorrectionStatus.PENDING) throw new DuplicateResourceException("This correction has already been reviewed");
        Instant now = Instant.now(clock);
        if (review.approve()) {
            requireSource();
            SimulationVoterRecord record = source.find(state.getVoterProfile().getIdNumberHash()).orElseThrow(() -> new EligibilityException("A matching fictional source record is required for approval"));
            if (!record.getReference().equals(review.sourceReference()) || record.getIdentityStatus() != VerificationStatus.VERIFIED
                    || !record.getFullName().trim().equalsIgnoreCase(state.getVoterProfile().getFullName().trim())
                    || !request.getLocation().sameAs(record.getLocation())
                    || (request.getLocation().getProvince() != null && record.getLocationStatus() != VerificationStatus.VERIFIED))
                throw new EligibilityException("The latest fictional source must verify this voter and the exact requested area");
            if (review.effectiveAt() == null || review.effectiveAt().isBefore(now)) throw new EligibilityException("Approval requires an effective date at or after the current time");
        }
        request.review(review.approve(), actor, review.reason().trim(), review.approve() ? review.sourceReference() : null,
                review.approve() ? review.effectiveAt() : null, now);
        audit.save(new ProfileVerificationAudit(state.getVoterProfile().getId(), actor, requestId,
                review.approve() ? "CORRECTION_APPROVED" : "CORRECTION_REJECTED", request.getSourceReference(), review.reason().trim(), now, request.getEffectiveAt()));
        return LocationCorrectionResponse.from(request);
    }
}
