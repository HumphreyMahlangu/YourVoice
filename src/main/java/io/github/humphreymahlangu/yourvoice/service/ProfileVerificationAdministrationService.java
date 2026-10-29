package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.SimulationVoterResponse;
import io.github.humphreymahlangu.yourvoice.dto.LocationCorrectionResponse;
import io.github.humphreymahlangu.yourvoice.dto.ProfileAuditResponse;
import io.github.humphreymahlangu.yourvoice.repository.SimulationVoterRecordRepository;
import io.github.humphreymahlangu.yourvoice.repository.LocationCorrectionRequestRepository;
import io.github.humphreymahlangu.yourvoice.repository.ProfileVerificationAuditRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProfileVerificationAdministrationService {
    private final SimulationVoterRecordRepository records;
    private final LocationCorrectionRequestRepository requests;
    private final ProfileVerificationAuditRepository audit;
    public ProfileVerificationAdministrationService(SimulationVoterRecordRepository records, LocationCorrectionRequestRepository requests, ProfileVerificationAuditRepository audit) {
        this.records = records; this.requests = requests; this.audit = audit;
    }
    @Transactional(readOnly = true)
    public Page<SimulationVoterResponse> records(int page, int size) { return records.findAllByOrderByRecordSequenceDesc(PageRequest.of(page, size)).map(SimulationVoterResponse::from); }
    @Transactional(readOnly = true)
    public Page<LocationCorrectionResponse> corrections(int page, int size) { return requests.findAllByOrderByRequestedAtDesc(PageRequest.of(page, size)).map(LocationCorrectionResponse::from); }
    @Transactional(readOnly = true)
    public Page<ProfileAuditResponse> audit(int page, int size) { return audit.findAllByOrderByOccurredAtDesc(PageRequest.of(page, size)).map(ProfileAuditResponse::from); }
}
