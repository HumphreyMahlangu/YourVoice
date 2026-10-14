package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.entity.ContestSigningKey;
import io.github.humphreymahlangu.votetrust.repository.ContestSigningKeyRepository;
import io.github.humphreymahlangu.votetrust.security.BlindSignatureService;
import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BallotSigningKeyService {

    private final ContestSigningKeyRepository contestSigningKeyRepository;
    private final BlindSignatureService blindSignatureService;
    private final Clock clock;
    // Keys never change once stored, so decrypted private keys can be cached for the life of the process.
    private final Map<UUID, RSAPrivateKey> privateKeyCache = new ConcurrentHashMap<>();

    public BallotSigningKeyService(
            ContestSigningKeyRepository contestSigningKeyRepository,
            BlindSignatureService blindSignatureService,
            Clock clock
    ) {
        this.contestSigningKeyRepository = contestSigningKeyRepository;
        this.blindSignatureService = blindSignatureService;
        this.clock = clock;
    }

    @Transactional
    public ContestSigningKey getOrCreate(UUID contestId) {
        return contestSigningKeyRepository.findByContestId(contestId).orElseGet(() -> {
            BlindSignatureService.GeneratedSigningKey generated = blindSignatureService.generateSigningKey(contestId);
            contestSigningKeyRepository.insertIfAbsent(
                    UUID.randomUUID(),
                    contestId,
                    generated.modulus(),
                    generated.publicExponent(),
                    generated.encryptedPrivateKey(),
                    Instant.now(clock)
            );
            return contestSigningKeyRepository.findByContestId(contestId)
                    .orElseThrow(() -> new IllegalStateException("Ballot signing key was not stored for contest " + contestId));
        });
    }

    @Transactional(readOnly = true)
    public Optional<ContestSigningKey> find(UUID contestId) {
        return contestSigningKeyRepository.findByContestId(contestId);
    }

    public RSAPrivateKey privateKey(ContestSigningKey signingKey) {
        return privateKeyCache.computeIfAbsent(
                signingKey.getContestId(),
                contestId -> blindSignatureService.decryptPrivateKey(signingKey.getEncryptedPrivateKey(), contestId)
        );
    }
}
