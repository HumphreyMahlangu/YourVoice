package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.SimulationVoterRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SimulationVoterRecordRepository extends JpaRepository<SimulationVoterRecord, UUID> {
    Optional<SimulationVoterRecord> findFirstByIdNumberHashOrderByRecordSequenceDesc(String idNumberHash);
    Page<SimulationVoterRecord> findAllByOrderByRecordSequenceDesc(Pageable pageable);
    boolean existsByReference(String reference);
}
