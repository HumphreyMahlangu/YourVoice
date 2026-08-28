package io.github.humphreymahlangu.votetrust.repository;

import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoterProfileRepository extends JpaRepository<VoterProfile, UUID> {

    Optional<VoterProfile> findByUserAccountId(UUID userAccountId);

    boolean existsByIdNumberHash(String idNumberHash);
}
