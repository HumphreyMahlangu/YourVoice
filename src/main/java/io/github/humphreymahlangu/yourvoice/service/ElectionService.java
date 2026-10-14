package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.ElectionResponse;
import io.github.humphreymahlangu.yourvoice.entity.Election;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.repository.ElectionRepository;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ElectionService {

    private final ElectionRepository electionRepository;

    public ElectionService(ElectionRepository electionRepository) {
        this.electionRepository = electionRepository;
    }

    @Transactional(readOnly = true)
    public Page<ElectionResponse> listElections(Pageable pageable) {
        return electionRepository.findAllByOrderByRegistrationStartAtDesc(pageable).map(ElectionResponse::from);
    }

    @Transactional(readOnly = true)
    public ElectionResponse getElection(UUID electionId) {
        Election election = electionRepository.findById(electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Election not found"));
        return ElectionResponse.from(election);
    }
}
