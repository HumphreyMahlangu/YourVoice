package io.github.humphreymahlangu.votetrust.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "ledger_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_states_contest", columnNames = "contest_id")
)
public class LedgerState {

    public static final String GENESIS_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @Column(name = "current_hash", nullable = false, length = 64)
    private String currentHash;

    @Column(name = "next_ledger_index", nullable = false)
    private Long nextLedgerIndex;

    @Column(name = "published_head_hash", length = 64)
    private String publishedHeadHash;

    @Column(name = "published_entry_count")
    private Long publishedEntryCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LedgerState() {
    }

    public LedgerState(Contest contest) {
        this.contest = contest;
        this.currentHash = GENESIS_HASH;
        this.nextLedgerIndex = 0L;
    }

    public UUID getId() {
        return id;
    }

    public Contest getContest() {
        return contest;
    }

    public String getCurrentHash() {
        return currentHash;
    }

    public Long getNextLedgerIndex() {
        return nextLedgerIndex;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void advanceTo(String currentHash) {
        this.currentHash = currentHash;
        this.nextLedgerIndex++;
    }

    public String getPublishedHeadHash() {
        return publishedHeadHash;
    }

    public Long getPublishedEntryCount() {
        return publishedEntryCount;
    }

    public boolean isPublished() {
        return publishedHeadHash != null;
    }

    public void markPublished(String publishedHeadHash, long publishedEntryCount) {
        this.publishedHeadHash = publishedHeadHash;
        this.publishedEntryCount = publishedEntryCount;
    }
}
