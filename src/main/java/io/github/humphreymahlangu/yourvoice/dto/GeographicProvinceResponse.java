package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.GeographicProvince;
import java.util.UUID;
public record GeographicProvinceResponse(UUID id, String name) {
    public static GeographicProvinceResponse from(GeographicProvince value) { return new GeographicProvinceResponse(value.getId(), value.getName()); }
}
