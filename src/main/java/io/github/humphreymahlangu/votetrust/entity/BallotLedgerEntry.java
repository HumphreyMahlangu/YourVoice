package io.github.humphreymahlangu.votetrust.entity;

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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "ballot_ledger_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_ballot_ledger_entries_contest_index",
                        columnNames = {"contest_id", "ledger_index"}
                ),
                @UniqueConstraint(
                        name = "uk_ballot_ledger_entries_contest_hash",
                        columnNames = {"contest_id", "current_hash"}
                )
        }
)
public class BallotLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_option_id", nullable = false)
    private ContestOption contestOption;

    @Column(name = "ledger_index", nullable = false)
    private Long ledgerIndex;

    @Column(name = "previous_hash", nullable = false, length = 64)
    private String previousHash;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(nullable = false, length = 96)
    private String nonce;

    // Version 1 entries hold the exact cast instant; version 2 entries hold only the UTC recording day.
    @Column(name = "cast_at", nullable = false)
    private Instant castAt;

    @Column(name = "hash_version", nullable = false)
    private short hashVersion;

    protected BallotLedgerEntry() {
    }

    public BallotLedgerEntry(
            Contest contest,
            ContestOption contestOption,
            Long ledgerIndex,
            String previousHash,
            String currentHash,
            String nonce,
            Instant castAt,
            short hashVersion
    ) {
        this.contest = contest;
        this.contestOption = contestOption;
        this.ledgerIndex = ledgerIndex;
        this.previousHash = previousHash;
        this.currentHash = currentHash;
        this.nonce = nonce;
        this.castAt = castAt;
        this.hashVersion = hashVersion;
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

    public Long getLedgerIndex() {
        return ledgerIndex;
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

    public Instant getCastAt() {
        return castAt;
    }

    public short getHashVersion() {
        return hashVersion;
    }
}
