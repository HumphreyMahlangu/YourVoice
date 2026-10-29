package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.RegisteredLocation;
import java.util.UUID;
public record RegisteredLocationResponse(UUID provinceId, String province, UUID municipalityId, String municipality,
        UUID votingDistrictId, String districtCode, String districtName, Integer wardNumber) {
    public static RegisteredLocationResponse from(RegisteredLocation area) {
        if (area == null) area = RegisteredLocation.nationalOnly();
        return new RegisteredLocationResponse(area.getProvince() == null ? null : area.getProvince().getId(), area.provinceName(),
                area.getMunicipality() == null ? null : area.getMunicipality().getId(), area.municipalityName(),
                area.getDistrict() == null ? null : area.getDistrict().getId(),
                area.getDistrict() == null ? null : area.getDistrict().getCode(),
                area.getDistrict() == null ? null : area.getDistrict().getName(), area.wardNumber());
    }
}
