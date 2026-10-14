package io.github.humphreymahlangu.yourvoice.observability;

import io.github.humphreymahlangu.yourvoice.entity.ElectionLifecycleOutcome;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Number of active elections whose automatic transition out of their current status has failed. A non-zero value
 * needs an administrator (usually: finish the ballot configuration, or cancel). Alert on it.
 */
@Component
public class StuckElectionsGauge {

    private static final List<ElectionStatus> ACTIVE_STATUSES = List.of(
            ElectionStatus.DRAFT,
            ElectionStatus.REGISTRATION_OPEN,
            ElectionStatus.REGISTRATION_CLOSED,
            ElectionStatus.VOTING_OPEN
    );

    public StuckElectionsGauge(MeterRegistry meterRegistry, ElectionRepository electionRepository) {
        Gauge.builder("yourvoice.elections.stuck", electionRepository,
                        repository -> repository.countWithFailedTransitionFromCurrentStatus(
                                ACTIVE_STATUSES,
                                ElectionLifecycleOutcome.FAILURE
                        ))
                .description("Active elections whose automatic transition from the current status failed")
                .register(meterRegistry);
    }
}
