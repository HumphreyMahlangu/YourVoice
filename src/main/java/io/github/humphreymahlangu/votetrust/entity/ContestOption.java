package io.github.humphreymahlangu.votetrust.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "contest_options",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_contest_options_contest_name", columnNames = {"contest_id", "name"}),
                @UniqueConstraint(name = "uk_contest_options_contest_order", columnNames = {"contest_id", "display_order"})
        }
)
public class ContestOption {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "contest_id", nullable = false)
    private Contest contest;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "option_type", nullable = false, length = 32)
    private ContestOptionType optionType;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContestOption() {
    }

    public ContestOption(Contest contest, String name, ContestOptionType optionType, Integer displayOrder) {
        this.contest = contest;
        this.name = name;
        this.optionType = optionType;
        this.displayOrder = displayOrder;
    }

    public UUID getId() {
        return id;
    }

    public Contest getContest() {
        return contest;
    }

    public String getName() {
        return name;
    }

    public ContestOptionType getOptionType() {
        return optionType;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    /** Replaces the option definition while its contest is still a draft. */
    public void updateDetails(String name, ContestOptionType optionType, Integer displayOrder) {
        this.name = name;
        this.optionType = optionType;
        this.displayOrder = displayOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
