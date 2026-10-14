package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.BallotLedgerEntry;
import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ContestOption;
import io.github.humphreymahlangu.yourvoice.entity.LedgerState;
import io.github.humphreymahlangu.yourvoice.entity.PublishedBallotEntry;
import io.github.humphreymahlangu.yourvoice.repository.BallotLedgerEntryRepository;
import io.github.humphreymahlangu.yourvoice.repository.LedgerStateRepository;
import io.github.humphreymahlangu.yourvoice.repository.PublishedBallotEntryRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the public ledger of a closed contest.
 *
 * <p>The internal ledger is chained in cast order, which would let anyone who knows when each voter signed in
 * map ledger positions back to voters. The public ledger instead chains the same ballots in a cryptographically
 * random order, with fresh nonces, under a hash format whose inputs are all published. Third parties can recompute
 * every public hash themselves; the server-side audit additionally checks that both ledgers hold the same ballots.
 */
@Service
public class LedgerPublicationService {

    public static final String PUBLIC_HASH_DOMAIN = "votetrust-public-ledger-v1";

    private final VoteHashChainService voteHashChainService;
    private final LedgerStateRepository ledgerStateRepository;
    private final BallotLedgerEntryRepository ballotLedgerEntryRepository;
    private final PublishedBallotEntryRepository publishedBallotEntryRepository;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public LedgerPublicationService(
            VoteHashChainService voteHashChainService,
            LedgerStateRepository ledgerStateRepository,
            BallotLedgerEntryRepository ballotLedgerEntryRepository,
            PublishedBallotEntryRepository publishedBallotEntryRepository,
            Clock clock
    ) {
        this.voteHashChainService = voteHashChainService;
        this.ledgerStateRepository = ledgerStateRepository;
        this.ballotLedgerEntryRepository = ballotLedgerEntryRepository;
        this.publishedBallotEntryRepository = publishedBallotEntryRepository;
        this.clock = clock;
    }

    /**
     * Publishes the contest ledger once; later calls return the existing publication. Callers must only publish
     * contests whose voting has closed. Ballot appends check the publication flag under the same row lock, so no
     * ballot can be added after publication.
     */
    @Transactional
    public LedgerState ensurePublished(Contest contest) {
        // Check with a query that does not load the entity: a LedgerState loaded here would sit in the persistence
        // context and could mask the state another transaction commits while we wait for the row lock below.
        if (ledgerStateRepository.existsByContestIdAndPublishedHeadHashIsNotNull(contest.getId())) {
            return ledgerStateRepository.findByContestId(contest.getId()).orElseThrow();
        }

        LedgerState ledgerState = voteHashChainService.lockLedgerState(contest, Instant.now(clock));
        if (ledgerState.isPublished()) {
            return ledgerState;
        }

        List<ContestOption> selections = new ArrayList<>();
        for (BallotLedgerEntry entry : ballotLedgerEntryRepository.findByContestIdOrderByLedgerIndexAsc(contest.getId())) {
            selections.add(entry.getContestOption());
        }
        Collections.shuffle(selections, secureRandom);

        List<PublishedBallotEntry> publishedEntries = new ArrayList<>(selections.size());
        String previousHash = LedgerState.GENESIS_HASH;
        long position = 0;
        for (ContestOption selection : selections) {
            String nonce = voteHashChainService.generateNonce();
            String currentHash = calculatePublicHash(contest.getId(), selection.getId(), position, previousHash, nonce);
            publishedEntries.add(new PublishedBallotEntry(contest, selection, position, previousHash, currentHash, nonce));
            previousHash = currentHash;
            position++;
        }
        publishedBallotEntryRepository.saveAll(publishedEntries);
        ledgerState.markPublished(previousHash, publishedEntries.size());
        return ledgerState;
    }

    /**
     * {@code SHA-256("votetrust-public-ledger-v1|contestId|contestOptionId|position|previousHash|nonce")} as
     * lowercase hex. Every input is returned by the public ledger endpoint.
     */
    public String calculatePublicHash(
            UUID contestId,
            UUID contestOptionId,
            long position,
            String previousHash,
            String nonce
    ) {
        return VoteHashChainService.sha256Hex(String.join(
                "|",
                PUBLIC_HASH_DOMAIN,
                contestId.toString(),
                contestOptionId.toString(),
                Long.toString(position),
                previousHash,
                nonce
        ));
    }
}
