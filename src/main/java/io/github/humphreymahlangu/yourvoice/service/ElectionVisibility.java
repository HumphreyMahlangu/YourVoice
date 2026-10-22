package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.entity.ElectionStatus;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import java.util.Set;

final class ElectionVisibility {

    static final Set<ElectionStatus> PUBLIC_STATUSES = Set.of(
            ElectionStatus.REGISTRATION_OPEN,
            ElectionStatus.REGISTRATION_CLOSED,
            ElectionStatus.VOTING_OPEN,
            ElectionStatus.COMPLETED
    );

    private ElectionVisibility() {
    }

    static void requireVisible(Election election, boolean administrator) {
        if (!administrator && !PUBLIC_STATUSES.contains(election.getStatus())) {
            throw new ResourceNotFoundException("Election not found");
        }
    }
}
