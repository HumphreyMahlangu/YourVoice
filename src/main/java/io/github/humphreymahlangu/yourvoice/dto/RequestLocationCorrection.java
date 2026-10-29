package io.github.humphreymahlangu.yourvoice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RequestLocationCorrection(@Valid LocationSelection location, @NotBlank @Size(max = 500) String reason) { }
