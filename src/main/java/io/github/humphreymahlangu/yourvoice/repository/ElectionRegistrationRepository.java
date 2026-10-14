package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.dto.DistrictRegistrationCount;
import io.github.humphreymahlangu.votetrust.entity.ElectionRegistration;
import io.github.humphreymahlangu.votetrust.entity.RegistrationStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ElectionRegistrationRepository extends JpaRepository<ElectionRegistration, UUID> {

    boolean existsByVoterProfileIdAndElectionId(UUID voterProfileId, UUID electionId);

    @EntityGraph(attributePaths = {"voterProfile", "votingDistrict"})
    Optional<ElectionRegistration> findByVoterProfileUserAccountIdAndElectionIdAndStatus(
            UUID userAccountId,
            UUID electionId,
            RegistrationStatus status
    );

    long countByElectionIdAndStatus(UUID electionId, RegistrationStatus status);

    @EntityGraph(attributePaths = {"election", "votingDistrict"})
    Page<ElectionRegistration> findByVoterProfileUserAccountIdOrderByRegisteredAtDesc(UUID userAccountId, Pageable pageable);

    @Query("""
            select new io.github.humphreymahlangu.votetrust.dto.DistrictRegistrationCount(
                district.province,
                district.municipality,
                district.wardNumber,
                count(registration.id)
            )
            from ElectionRegistration registration
            join registration.votingDistrict district
            where registration.election.id = :electionId
              and registration.status = :status
            group by district.province, district.municipality, district.wardNumber
            """)
    List<DistrictRegistrationCount> countByDistrict(
            @Param("electionId") UUID electionId,
            @Param("status") RegistrationStatus status
    );

    boolean existsByVotingDistrictId(UUID votingDistrictId);
}
