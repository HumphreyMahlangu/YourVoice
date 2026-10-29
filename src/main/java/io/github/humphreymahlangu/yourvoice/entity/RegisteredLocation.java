package io.github.humphreymahlangu.yourvoice.entity;

import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.util.Objects;

/** Server-resolved stable geographic records. Never attached to an anonymous ballot. */
@Embeddable
public class RegisteredLocation {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id")
    private GeographicProvince province;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipality_id")
    private GeographicMunicipality municipality;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id")
    private VotingDistrict district;
    protected RegisteredLocation() { }
    public RegisteredLocation(GeographicProvince province, GeographicMunicipality municipality, VotingDistrict district) {
        this.province = province;
        this.municipality = municipality;
        this.district = district;
    }
    public static RegisteredLocation nationalOnly() { return new RegisteredLocation(null, null, null); }
    public GeographicProvince getProvince() { return province; }
    public GeographicMunicipality getMunicipality() { return municipality; }
    public VotingDistrict getDistrict() { return district; }
    public String provinceName() { return province == null ? null : province.getName(); }
    public String municipalityName() { return municipality == null ? null : municipality.getName(); }
    public Integer wardNumber() { return district == null ? null : district.getWardNumber(); }
    public boolean sameAs(RegisteredLocation other) {
        if (other == null) return province == null && municipality == null && district == null;
        return Objects.equals(province == null ? null : province.getId(), other.province == null ? null : other.province.getId())
                && Objects.equals(municipality == null ? null : municipality.getId(), other.municipality == null ? null : other.municipality.getId())
                && Objects.equals(district == null ? null : district.getId(), other.district == null ? null : other.district.getId());
    }
}
