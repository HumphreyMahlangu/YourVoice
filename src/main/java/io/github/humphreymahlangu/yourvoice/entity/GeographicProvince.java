package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "geographic_provinces")
public class GeographicProvince {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(name = "normalised_name", nullable = false, unique = true, length = 80)
    private String normalisedName;
    protected GeographicProvince() { }
    public GeographicProvince(String name) {
        this.name = name.trim();
        this.normalisedName = this.name.toLowerCase(Locale.ROOT);
    }
    public UUID getId() { return id; }
    public String getName() { return name; }
}
