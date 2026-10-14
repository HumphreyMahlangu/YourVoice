package io.github.humphreymahlangu.yourvoice.dto;

import io.github.humphreymahlangu.yourvoice.entity.ContestOptionType;
import java.util.UUID;

public record ContestOptionTallyRow(
        UUID contestOptionId,
        String name,
        ContestOptionType optionType,
        Integer displayOrder,
        Long voteCount
) {
}
