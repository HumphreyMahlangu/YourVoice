package io.github.humphreymahlangu.votetrust.entity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.votetrust.dto.BallotCastResponse;
import java.lang.reflect.Field;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BallotAnonymitySchemaTest {

    @Test
    void ballotLedgerEntryDoesNotContainVoterLinkFields() {
        assertThat(fieldNames(BallotLedgerEntry.class))
                .doesNotContain("userAccount", "voterProfile", "votingRight", "credential");
    }

    @Test
    void anonymousCredentialDoesNotContainVoterLinkFields() {
        assertThat(fieldNames(AnonymousVotingCredential.class))
                .doesNotContain("userAccount", "voterProfile", "votingRight", "issuedAt", "usedAt");
    }

    @Test
    void votingRightDoesNotStoreCredentialTimingFields() {
        assertThat(fieldNames(VotingRight.class))
                .doesNotContain("credentialIssuedAt", "createdAt", "updatedAt");
    }

    @Test
    void votingRightStoresOnlyTheBlindedTokenNeverTheBallotTokenOrSignature() {
        assertThat(fieldNames(VotingRight.class))
                .contains("blindedTokenHash")
                .doesNotContain("ballotToken", "ballotTokenHash", "signature", "credentialHash");
    }

    @Test
    void ballotSigningKeyAndPublishedLedgerDoNotContainVoterLinkFields() {
        assertThat(fieldNames(ContestSigningKey.class))
                .doesNotContain("userAccount", "voterProfile", "votingRight");
        assertThat(fieldNames(PublishedBallotEntry.class))
                .doesNotContain("userAccount", "voterProfile", "votingRight", "ledgerIndex", "castAt");
    }

    @Test
    void ballotCastResponseDoesNotContainReceiptLikeLedgerFields() {
        assertThat(fieldNames(BallotCastResponse.class))
                .doesNotContain("ballotLedgerEntryId", "ledgerIndex", "previousHash", "currentHash", "castAt");
    }

    private String[] fieldNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredFields())
                .map(Field::getName)
                .toArray(String[]::new);
    }
}
