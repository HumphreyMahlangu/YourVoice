package io.github.humphreymahlangu.votetrust.service;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.humphreymahlangu.votetrust.repository.ElectionRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ElectionLifecycleCoordinatorTest {

    @Mock
    private ElectionRepository electionRepository;

    @Mock
    private ElectionLifecycleService lifecycleService;

    @Test
    void unexpectedFailureForOneElectionDoesNotBlockTheOthers() {
        UUID failingElectionId = UUID.randomUUID();
        UUID healthyElectionId = UUID.randomUUID();
        when(electionRepository.findIdsByStatusIn(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(failingElectionId, healthyElectionId));
        doThrow(new IllegalStateException("database connection interrupted"))
                .when(lifecycleService).advanceElection(failingElectionId);

        ElectionLifecycleCoordinator coordinator = new ElectionLifecycleCoordinator(
                electionRepository,
                lifecycleService
        );
        coordinator.synchronizeElectionStates();

        verify(lifecycleService).advanceElection(failingElectionId);
        verify(lifecycleService).advanceElection(healthyElectionId);
    }
}
