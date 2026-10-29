package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.SimulationVoterRecord;
import io.github.humphreymahlangu.yourvoice.repository.SimulationVoterRecordRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class RegistrySimulationVerificationSource implements SimulationVerificationSource {
    private final SimulationVoterRecordRepository records;
    private final boolean enabled;
    public RegistrySimulationVerificationSource(SimulationVoterRecordRepository records,
            @Value("${yourvoice.simulation-verification.enabled:true}") boolean enabled) {
        this.records = records; this.enabled = enabled;
    }
    public boolean available() { return enabled; }
    public Optional<SimulationVoterRecord> find(String protectedIdentity) {
        return enabled ? records.findFirstByIdNumberHashOrderByRecordSequenceDesc(protectedIdentity) : Optional.empty();
    }
}
