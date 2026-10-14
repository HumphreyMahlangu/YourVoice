package io.github.humphreymahlangu.votetrust.dto;

import io.github.humphreymahlangu.votetrust.entity.ContestOptionType;
import java.util.UUID;

public record ContestOptionTallyRow(
        UUID contestOptionId,
        String name,
        ContestOptionType optionType,
        Integer displayOrder,
        Long voteCount
) {
}
