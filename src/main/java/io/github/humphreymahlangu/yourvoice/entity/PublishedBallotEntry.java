package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * A ballot in the public ledger of a closed contest. Entries are shuffled before chaining, so their position is
 * independent of cast order, and they carry a fresh nonce so they cannot be matched to internal ledger entries.
 */
@Entity
@Table(
        name = "published_ballot_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_published_ballot_entries_contest_position",
                        columnNames = {"contest_id", "position"}
                ),
                @UniqueConstraint(
                        name = "uk_published_ballot_entries_contest_hash",
                        columnNames = {"contest_id", "current_hash"}
                )
        }
)
public class PublishedBallotEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_option_id", nullable = false)
    private ContestOption contestOption;

    @Column(nullable = false)
    private Long position;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(nullable = false, length = 96)
    private String nonce;

    protected PublishedBallotEntry() {
    }

    public PublishedBallotEntry(
            Contest contest,
            ContestOption contestOption,
            Long position,
            String previousHash,
            String currentHash,
            String nonce
    ) {
        this.contest = contest;
        this.contestOption = contestOption;
        this.position = position;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
        this.nonce = nonce;
    }

    public UUID getId() {
        return id;
    }

    public Contest getContest() {
        return contest;
    }

    public ContestOption getContestOption() {
        return contestOption;
    }

    public Long getPosition() {
        return position;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public String getNonce() {
        return nonce;
    }
}
