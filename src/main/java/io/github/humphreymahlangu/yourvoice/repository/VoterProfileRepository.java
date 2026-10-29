package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.VoterProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoterProfileRepository extends JpaRepository<VoterProfile, UUID> {

    Optional<VoterProfile> findByUserAccountId(UUID userAccountId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from VoterProfile p where p.userAccount.id = :userAccountId")
    Optional<VoterProfile> findByUserAccountIdForUpdate(UUID userAccountId);

    boolean existsByIdNumberHash(String idNumberHash);

    boolean existsByVotingDistrictId(UUID votingDistrictId);
}
