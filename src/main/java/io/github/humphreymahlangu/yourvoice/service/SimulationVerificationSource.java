package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.SimulationVoterRecord;
import java.util.Optional;
/** Boundary for the explicitly fictional source. No government/IEC adapter exists. */
public interface SimulationVerificationSource {
    boolean available();
    Optional<SimulationVoterRecord> find(String protectedIdentity);
}
