package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.dto.CreateGeographyRequest;
import io.github.humphreymahlangu.yourvoice.dto.GeographicProvinceResponse;
import io.github.humphreymahlangu.yourvoice.dto.GeographicMunicipalityResponse;
import io.github.humphreymahlangu.yourvoice.dto.LocationSelection;
import io.github.humphreymahlangu.yourvoice.entity.GeographicProvince;
import io.github.humphreymahlangu.yourvoice.entity.GeographicMunicipality;
import io.github.humphreymahlangu.yourvoice.entity.RegisteredLocation;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;
import io.github.humphreymahlangu.yourvoice.exception.EligibilityException;
import io.github.humphreymahlangu.yourvoice.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.yourvoice.repository.GeographicProvinceRepository;
import io.github.humphreymahlangu.yourvoice.repository.GeographicMunicipalityRepository;
import io.github.humphreymahlangu.yourvoice.repository.VotingDistrictRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class GeographicRecordService {
    private final GeographicProvinceRepository provinces;
    private final GeographicMunicipalityRepository municipalities;
    private final VotingDistrictRepository districts;
    public GeographicRecordService(GeographicProvinceRepository provinces, GeographicMunicipalityRepository municipalities, VotingDistrictRepository districts) {
        this.provinces = provinces; this.municipalities = municipalities; this.districts = districts;
    }
    @Transactional(readOnly = true)
    public List<GeographicProvinceResponse> provinces() { return provinces.findAllByOrderByNameAsc().stream().map(GeographicProvinceResponse::from).toList(); }
    @Transactional(readOnly = true)
    public List<GeographicMunicipalityResponse> municipalities() { return municipalities.findAllByOrderByNameAsc().stream().map(GeographicMunicipalityResponse::from).toList(); }
    @Transactional
    public GeographicProvinceResponse createProvince(CreateGeographyRequest request) {
        if (request.provinceId() != null) throw new EligibilityException("A province cannot have a parent province");
        return GeographicProvinceResponse.from(province(request.name()));
    }
    @Transactional
    public GeographicMunicipalityResponse createMunicipality(CreateGeographyRequest request) {
        if (request.provinceId() == null) throw new EligibilityException("Choose a province for this municipality");
        GeographicProvince parent = provinces.findById(request.provinceId()).orElseThrow(() -> new ResourceNotFoundException("Province not found"));
        return GeographicMunicipalityResponse.from(municipality(parent, request.name()));
    }
    @Transactional
    public void ensureParents(String province, String municipality) { municipality(province(province), municipality); }
    private GeographicProvince province(String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        provinces.insertIfAbsent(UUID.randomUUID(), name.trim(), key);
        return provinces.findByNormalisedName(key).orElseThrow();
    }
    private GeographicMunicipality municipality(GeographicProvince parent, String name) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        municipalities.insertIfAbsent(UUID.randomUUID(), parent.getId(), name.trim(), key);
        return municipalities.findByProvinceIdAndNormalisedName(parent.getId(), key).orElseThrow();
    }
    @Transactional(readOnly = true)
    public RegisteredLocation resolve(LocationSelection selection) {
        if (selection == null) return RegisteredLocation.nationalOnly();
        VotingDistrict district = selection.votingDistrictId() == null ? null : districts.findById(selection.votingDistrictId())
                .orElseThrow(() -> new ResourceNotFoundException("Area not found"));
        GeographicMunicipality municipality = selection.municipalityId() == null ? null : municipalities.findById(selection.municipalityId())
                .orElseThrow(() -> new ResourceNotFoundException("Municipality not found"));
        GeographicProvince province = selection.provinceId() == null ? null : provinces.findById(selection.provinceId())
                .orElseThrow(() -> new ResourceNotFoundException("Province not found"));
        if (district != null) {
            GeographicProvince parent = provinces.findByNormalisedName(district.getProvince().trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new EligibilityException("This area's province needs administrator configuration"));
            GeographicMunicipality localParent = municipalities.findByProvinceIdAndNormalisedName(parent.getId(), district.getMunicipality().trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new EligibilityException("This area's municipality needs administrator configuration"));
            if ((province != null && !province.getId().equals(parent.getId())) || (municipality != null && !municipality.getId().equals(localParent.getId())))
                throw new EligibilityException("The area does not belong to the selected province and municipality");
            province = parent; municipality = localParent;
        }
        if (municipality != null) {
            if (province != null && !province.getId().equals(municipality.getProvince().getId()))
                throw new EligibilityException("The municipality does not belong to the selected province");
            province = municipality.getProvince();
        }
        return new RegisteredLocation(province, municipality, district);
    }
}
