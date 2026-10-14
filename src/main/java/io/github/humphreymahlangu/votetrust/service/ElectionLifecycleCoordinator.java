package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import java.util.List;
import java.util.UUID;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "votetrust.lifecycle.scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class ElectionLifecycleCoordinator {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElectionLifecycleCoordinator.class);
    private static final List<ElectionStatus> ACTIVE_STATUSES = List.of(
            ElectionStatus.DRAFT,
            ElectionStatus.REGISTRATION_OPEN,
            ElectionStatus.REGISTRATION_CLOSED,
            ElectionStatus.VOTING_OPEN
    );

    private final ElectionRepository electionRepository;
    private final ElectionLifecycleService electionLifecycleService;

    public ElectionLifecycleCoordinator(
            ElectionRepository electionRepository,
            ElectionLifecycleService electionLifecycleService
    ) {
        this.electionRepository = electionRepository;
        this.electionLifecycleService = electionLifecycleService;
    }

    @Scheduled(
            initialDelayString = "${votetrust.lifecycle.poll-interval-ms:5000}",
            fixedDelayString = "${votetrust.lifecycle.poll-interval-ms:5000}"
    )
    @SchedulerLock(name = "electionLifecycleCoordinator", lockAtMostFor = "PT1M", lockAtLeastFor = "PT1S")
    public void synchronizeElectionStates() {
        List<UUID> electionIds = electionRepository.findIdsByStatusIn(ACTIVE_STATUSES);
        for (UUID electionId : electionIds) {
            try {
                electionLifecycleService.advanceElection(electionId);
            } catch (RuntimeException exception) {
                LOGGER.error("Unexpected failure while advancing election {}", electionId, exception);
            }
        }
    }
}
