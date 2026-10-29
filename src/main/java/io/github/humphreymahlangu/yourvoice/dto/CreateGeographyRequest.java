package io.github.humphreymahlangu.yourvoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
public record CreateGeographyRequest(@NotBlank @Size(max = 80) String name, UUID provinceId) { }
