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
        name = "contests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_contests_election_name",
                columnNames = {"election_id", "name"}
        )
)
public class Contest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "election_id", nullable = false)
    private Election election;

    @Column(nullable = false, length = 160)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ContestStatus status;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "scope_province", length = 80)
    private String scopeProvince;

    @Column(name = "scope_municipality", length = 160)
    private String scopeMunicipality;

    @Column(name = "scope_ward_number")
    private Integer scopeWardNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Contest() {
    }

    public Contest(Election election, String name, ContestType type, ContestStatus status, Integer displayOrder) {
        this(election, name, type, status, displayOrder, null, null, null);
    }

    public Contest(
            Election election,
            String name,
            ContestType type,
            ContestStatus status,
            Integer displayOrder,
            String scopeProvince,
            String scopeMunicipality,
            Integer scopeWardNumber
    ) {
        this.election = election;
        this.name = name;
        this.type = type;
        this.status = status;
        this.displayOrder = displayOrder;
        this.scopeProvince = scopeProvince;
        this.scopeMunicipality = scopeMunicipality;
        this.scopeWardNumber = scopeWardNumber;
    }

    public UUID getId() {
        return id;
    }

    public Election getElection() {
        return election;
    }

    public String getName() {
        return name;
    }

    public ContestType getType() {
        return type;
    }

    public ContestStatus getStatus() {
        return status;
    }

    public void transitionTo(ContestStatus status) {
        this.status = status;
    }

    /** Replaces the contest definition while its ballot is still being configured. */
    public void updateDetails(
            String name,
            ContestType type,
            Integer displayOrder,
            String scopeProvince,
            String scopeMunicipality,
            Integer scopeWardNumber
    ) {
        this.name = name;
        this.type = type;
        this.displayOrder = displayOrder;
        this.scopeProvince = scopeProvince;
        this.scopeMunicipality = scopeMunicipality;
        this.scopeWardNumber = scopeWardNumber;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getScopeProvince() {
        return scopeProvince;
    }

    public String getScopeMunicipality() {
        return scopeMunicipality;
    }

    public Integer getScopeWardNumber() {
        return scopeWardNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
