package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.entity.BallotLedgerEntry;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.LedgerState;
import io.github.humphreymahlangu.votetrust.exception.VotingClosedException;
import io.github.humphreymahlangu.votetrust.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.votetrust.repository.LedgerStateRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteHashChainService {

    /** Version 1 hashed the exact cast instant. Kept so ledgers written before version 2 still verify. */
    public static final short HASH_VERSION_EXACT_TIMESTAMP = 1;

    /** Version 2 stores and hashes only the UTC recording day, so the database holds no precise cast time. */
    public static final short HASH_VERSION_RECORDING_DAY = 2;

    private static final int NONCE_BYTES = 24;

    private final LedgerStateRepository ledgerStateRepository;
    private final BallotLedgerEntryRepository ballotLedgerEntryRepository;
    private final EntityManager entityManager;
    private final SecureRandom secureRandom = new SecureRandom();

    public VoteHashChainService(
            LedgerStateRepository ledgerStateRepository,
            BallotLedgerEntryRepository ballotLedgerEntryRepository,
            EntityManager entityManager
    ) {
        this.ledgerStateRepository = ledgerStateRepository;
        this.ballotLedgerEntryRepository = ballotLedgerEntryRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public BallotLedgerEntry appendVote(Contest contest, ContestOption contestOption, Instant castAt) {
        LedgerState ledgerState = lockLedgerState(contest, castAt);
        if (ledgerState.isPublished()) {
            // The contest closed and its public ledger was built while this ballot waited for the lock.
            throw new VotingClosedException("Election voting period is closed");
        }

        Long ledgerIndex = ledgerState.getNextLedgerIndex();
        String previousHash = ledgerState.getCurrentHash();
        String nonce = generateNonce();
        Instant recordedAt = castAt.truncatedTo(ChronoUnit.DAYS);
        String currentHash = calculateHash(
                HASH_VERSION_RECORDING_DAY,
                contest.getId(),
                contestOption.getId(),
                ledgerIndex,
                previousHash,
                nonce,
                recordedAt
        );

        BallotLedgerEntry entry = ballotLedgerEntryRepository.save(new BallotLedgerEntry(
                contest,
                contestOption,
                ledgerIndex,
                previousHash,
                currentHash,
                nonce,
                recordedAt,
                HASH_VERSION_RECORDING_DAY
        ));
        ledgerState.advanceTo(currentHash);

        return entry;
    }

    /**
     * Returns the contest's ledger state locked FOR UPDATE, creating the genesis state first if needed.
     * Every writer to a contest's ledger serializes on this row lock.
     */
    @Transactional
    public LedgerState lockLedgerState(Contest contest, Instant now) {
        ledgerStateRepository.insertIfAbsent(UUID.randomUUID(), contest.getId(), LedgerState.GENESIS_HASH, now);
        LedgerState ledgerState = ledgerStateRepository.findByContestIdForUpdate(contest.getId())
                .orElseThrow(() -> new IllegalStateException("Ledger state was not created for contest " + contest.getId()));
        // If this persistence context already held the entity, the locking query returns that cached instance
        // unchanged. Refresh so callers always see the row as committed by whoever held the lock before us.
        entityManager.refresh(ledgerState);
        return ledgerState;
    }

    public String calculateHash(BallotLedgerEntry entry) {
        return calculateHash(
                entry.getHashVersion(),
                entry.getContest().getId(),
                entry.getContestOption().getId(),
                entry.getLedgerIndex(),
                entry.getPreviousHash(),
                entry.getNonce(),
                entry.getCastAt()
        );
    }

    public String calculateHash(
            short hashVersion,
            UUID contestId,
            UUID contestOptionId,
            Long ledgerIndex,
            String previousHash,
            String nonce,
            Instant castAt
    ) {
        String domain = switch (hashVersion) {
            case HASH_VERSION_EXACT_TIMESTAMP -> "votetrust-ledger-v1";
            case HASH_VERSION_RECORDING_DAY -> "votetrust-ledger-v2";
            default -> throw new IllegalArgumentException("Unsupported ledger hash version " + hashVersion);
        };
        return sha256Hex(String.join(
                "|",
                domain,
                contestId.toString(),
                contestOptionId.toString(),
                ledgerIndex.toString(),
                previousHash,
                nonce,
                castAt.toString()
        ));
    }

    String generateNonce() {
        byte[] bytes = new byte[NONCE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonicalPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate ballot ledger hash", exception);
        }
    }
}
