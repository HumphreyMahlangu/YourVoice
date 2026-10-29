package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VotingDistrictRepository extends JpaRepository<VotingDistrict, UUID> {

    Page<VotingDistrict> findAllByOrderByProvinceAscMunicipalityAscWardNumberAscNameAsc(Pageable pageable);

    @org.springframework.data.jpa.repository.Query(value = "select exists(select 1 from simulation_voter_records where district_id = :id union all select 1 from voter_verifications where district_id = :id union all select 1 from location_correction_requests where district_id = :id)", nativeQuery = true)
    boolean isUsedByVerification(UUID id);

    @org.springframework.data.jpa.repository.Query("select (count(d) > 0) from VotingDistrict d where lower(trim(d.province)) = lower(trim(:province)) and lower(trim(d.municipality)) = lower(trim(:municipality)) and d.wardNumber = :ward")
    boolean existsInHierarchy(String province, String municipality, Integer ward);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
