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
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "geographic_municipalities")
public class GeographicMunicipality {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    private GeographicProvince province;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(name = "normalised_name", nullable = false, length = 160)
    private String normalisedName;
    protected GeographicMunicipality() { }
    public GeographicMunicipality(GeographicProvince province, String name) {
        this.province = province;
        this.name = name.trim();
        this.normalisedName = this.name.toLowerCase(Locale.ROOT);
    }
    public UUID getId() { return id; }
    public GeographicProvince getProvince() { return province; }
    public String getName() { return name; }
}
