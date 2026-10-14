package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.exception.EligibilityException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class SouthAfricanIdNumberValidator {

    private static final int ID_NUMBER_LENGTH = 13;
    private static final int CITIZENSHIP_DIGIT_INDEX = 10;

    private final Clock clock;

    public SouthAfricanIdNumberValidator(Clock clock) {
        this.clock = clock;
    }

    public ValidatedSouthAfricanId validateForVoterRegistration(String idNumber) {
        String normalizedIdNumber = normalize(idNumber);

        if (!normalizedIdNumber.matches("\\d{13}")) {
            throw new EligibilityException("A South African ID number must contain exactly 13 digits");
        }

        LocalDate dateOfBirth = parseDateOfBirth(normalizedIdNumber);
        if (!isChecksumValid(normalizedIdNumber)) {
            throw new EligibilityException("South African ID number checksum is invalid");
        }

        if (!isSouthAfricanCitizen(normalizedIdNumber)) {
            throw new EligibilityException("Only South African citizens may register to vote");
        }

        if (dateOfBirth.plusYears(16).isAfter(LocalDate.now(clock))) {
            throw new EligibilityException("Voters must be at least 16 years old to register");
        }

        return new ValidatedSouthAfricanId(normalizedIdNumber, dateOfBirth);
    }

    private String normalize(String idNumber) {
        return idNumber == null ? "" : idNumber.trim();
    }

    private LocalDate parseDateOfBirth(String idNumber) {
        int year = Integer.parseInt(idNumber.substring(0, 2));
        int month = Integer.parseInt(idNumber.substring(2, 4));
        int day = Integer.parseInt(idNumber.substring(4, 6));

        int currentYear = LocalDate.now(clock).getYear();
        int candidateYear = 2000 + year;
        if (candidateYear > currentYear) {
            candidateYear = 1900 + year;
        }

        try {
            return LocalDate.of(candidateYear, month, day);
        } catch (RuntimeException exception) {
            throw new EligibilityException("South African ID number contains an invalid date of birth");
        }
    }

    private boolean isSouthAfricanCitizen(String idNumber) {
        return idNumber.charAt(CITIZENSHIP_DIGIT_INDEX) == '0';
    }

    private boolean isChecksumValid(String idNumber) {
        int oddPositionSum = 0;
        for (int index = 0; index < ID_NUMBER_LENGTH - 1; index += 2) {
            oddPositionSum += Character.digit(idNumber.charAt(index), 10);
        }

        StringBuilder evenPositionDigits = new StringBuilder();
        for (int index = 1; index < ID_NUMBER_LENGTH - 1; index += 2) {
            evenPositionDigits.append(idNumber.charAt(index));
        }

        int doubledEvenPositions = Integer.parseInt(evenPositionDigits.toString()) * 2;
        int doubledEvenDigitSum = String.valueOf(doubledEvenPositions)
                .chars()
                .map(Character::getNumericValue)
                .sum();

        int calculatedCheckDigit = (10 - ((oddPositionSum + doubledEvenDigitSum) % 10)) % 10;
        int suppliedCheckDigit = Character.digit(idNumber.charAt(ID_NUMBER_LENGTH - 1), 10);

        return calculatedCheckDigit == suppliedCheckDigit;
    }

    public record ValidatedSouthAfricanId(String normalizedIdNumber, LocalDate dateOfBirth) {
    }
}
