package io.github.humphreymahlangu.yourvoice.repository;

import io.github.humphreymahlangu.yourvoice.entity.VoterVerification;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
public interface VoterVerificationRepository extends JpaRepository<VoterVerification, UUID> {
    Optional<VoterVerification> findByVoterProfileId(UUID profileId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from VoterVerification v where v.voterProfile.id = :profileId")
    Optional<VoterVerification> findForUpdate(UUID profileId);
    @Modifying(flushAutomatically = true)
    @Query(value = "insert into voter_verifications(id,voter_profile_id,identity_status,location_status,established,message) values (:id,:profileId,'UNVERIFIED','UNVERIFIED',false,'Request a check against the fictional simulation registry.') on conflict(voter_profile_id) do nothing", nativeQuery = true)
    int insertIfAbsent(UUID id, UUID profileId);
}
