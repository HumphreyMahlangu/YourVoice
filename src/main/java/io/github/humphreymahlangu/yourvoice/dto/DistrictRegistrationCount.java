package io.github.humphreymahlangu.yourvoice.dto;

/**
 * Number of registrations in an election grouped by the geographic fields used for contest eligibility.
 */
public record DistrictRegistrationCount(
        String province,
        String municipality,
        Integer wardNumber,
        Long registrationCount
) {
}
