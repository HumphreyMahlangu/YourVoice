package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.humphreymahlangu.votetrust.exception.EligibilityException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SouthAfricanIdNumberValidatorTest {

    private final SouthAfricanIdNumberValidator validator = new SouthAfricanIdNumberValidator(
            Clock.fixed(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void validatesCitizenWhoIsAtLeastSixteen() {
        SouthAfricanIdNumberValidator.ValidatedSouthAfricanId result =
                validator.validateForVoterRegistration("1001015000083");

        assertThat(result.normalizedIdNumber()).isEqualTo("1001015000083");
        assertThat(result.dateOfBirth()).isEqualTo(LocalDate.of(2010, 1, 1));
    }

    @Test
    void rejectsInvalidChecksum() {
        assertThatThrownBy(() -> validator.validateForVoterRegistration("1001015000084"))
                .isInstanceOf(EligibilityException.class)
                .hasMessage("South African ID number checksum is invalid");
    }

    @Test
    void rejectsPermanentResidentIdNumber() {
        assertThatThrownBy(() -> validator.validateForVoterRegistration("1101015000180"))
                .isInstanceOf(EligibilityException.class)
                .hasMessage("Only South African citizens may register to vote");
    }

    @Test
    void rejectsPersonYoungerThanSixteen() {
        assertThatThrownBy(() -> validator.validateForVoterRegistration("1501015000082"))
                .isInstanceOf(EligibilityException.class)
                .hasMessage("Voters must be at least 16 years old to register");
    }
}
