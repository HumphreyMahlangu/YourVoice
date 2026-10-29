package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.GeographicMunicipality;
import java.util.UUID;
public record GeographicMunicipalityResponse(UUID id, UUID provinceId, String name) {
    public static GeographicMunicipalityResponse from(GeographicMunicipality value) { return new GeographicMunicipalityResponse(value.getId(), value.getProvince().getId(), value.getName()); }
}
