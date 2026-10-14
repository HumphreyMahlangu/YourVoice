package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoterProfileRepository extends JpaRepository<VoterProfile, UUID> {

    Optional<VoterProfile> findByUserAccountId(UUID userAccountId);

    boolean existsByIdNumberHash(String idNumberHash);

    boolean existsByVotingDistrictId(UUID votingDistrictId);
}
