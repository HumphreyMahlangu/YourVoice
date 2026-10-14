package io.github.humphreymahlangu.votetrust.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(
        name = "voting_districts",
        uniqueConstraints = @UniqueConstraint(name = "uk_voting_districts_code", columnNames = "code")
)
public class VotingDistrict {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 80)
    private String province;

    @Column(nullable = false, length = 160)
    private String municipality;

    @Column(name = "ward_number", nullable = false)
    private Integer wardNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VotingDistrict() {
    }

    public VotingDistrict(String code, String name, String province, String municipality, Integer wardNumber) {
        this.code = code;
        this.name = name;
        this.province = province;
        this.municipality = municipality;
        this.wardNumber = wardNumber;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getProvince() {
        return province;
    }

    public String getMunicipality() {
        return municipality;
    }

    public Integer getWardNumber() {
        return wardNumber;
    }

    public void updateDetails(String code, String name, String province, String municipality, Integer wardNumber) {
        this.code = code;
        this.name = name;
        this.province = province;
        this.municipality = municipality;
        this.wardNumber = wardNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
