package io.github.humphreymahlangu.yourvoice.dto;

import java.util.UUID;
/** Only identifiers of configured server geography; hierarchy is validated in the service. */
public record LocationSelection(UUID provinceId, UUID municipalityId, UUID votingDistrictId) { }
