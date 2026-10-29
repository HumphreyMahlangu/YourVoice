package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.dto.DistrictRegistrationCount;
import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.RegistrationStatus;
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

    long countByElectionIdAndStatusAndIdentityVerifiedFalse(UUID electionId, RegistrationStatus status);

    @EntityGraph(attributePaths = {"election", "votingDistrict"})
    Page<ElectionRegistration> findByVoterProfileUserAccountIdOrderByRegisteredAtDesc(UUID userAccountId, Pageable pageable);

    @Query("""
            select new io.github.humphreymahlangu.yourvoice.dto.DistrictRegistrationCount(
                registration.registeredProvince,
                registration.registeredMunicipality,
                registration.registeredWardNumber,
                count(registration.id)
            )
            from ElectionRegistration registration
            where registration.election.id = :electionId
              and registration.status = :status
              and registration.identityVerified = true
            group by registration.registeredProvince, registration.registeredMunicipality, registration.registeredWardNumber
            """)
    List<DistrictRegistrationCount> countByDistrict(
            @Param("electionId") UUID electionId,
            @Param("status") RegistrationStatus status
    );

    Optional<ElectionRegistration> findByVoterProfileUserAccountIdAndElectionId(UUID userAccountId, UUID electionId);

    boolean existsByVotingDistrictId(UUID votingDistrictId);
}
