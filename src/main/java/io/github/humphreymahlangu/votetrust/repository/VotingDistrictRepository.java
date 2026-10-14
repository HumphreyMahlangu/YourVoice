package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotingDistrictRepository extends JpaRepository<VotingDistrict, UUID> {

    Page<VotingDistrict> findAllByOrderByProvinceAscMunicipalityAscWardNumberAscNameAsc(Pageable pageable);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
