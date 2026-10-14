package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.VotingDistrictResponse;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VotingDistrictService {

    private final VotingDistrictRepository votingDistrictRepository;

    public VotingDistrictService(VotingDistrictRepository votingDistrictRepository) {
        this.votingDistrictRepository = votingDistrictRepository;
    }

    @Transactional(readOnly = true)
    public Page<VotingDistrictResponse> listVotingDistricts(Pageable pageable) {
        return votingDistrictRepository.findAllByOrderByProvinceAscMunicipalityAscWardNumberAscNameAsc(pageable)
                .map(VotingDistrictResponse::from);
    }
}
