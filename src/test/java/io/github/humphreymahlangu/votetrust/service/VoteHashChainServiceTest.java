package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoteHashChainServiceTest {

    private static final UUID CONTEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OPTION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant CAST_AT = Instant.parse("2026-08-07T10:00:00Z");

    private final VoteHashChainService service = new VoteHashChainService(null, null);

    @Test
    void hashChangesWhenPreviousHashChanges() {
        String firstHash = service.calculateHash(
                VoteHashChainService.HASH_VERSION_RECORDING_DAY, CONTEST_ID, OPTION_ID, 0L, "0".repeat(64), "nonce", CAST_AT);
        String tamperedPreviousHash = service.calculateHash(
                VoteHashChainService.HASH_VERSION_RECORDING_DAY, CONTEST_ID, OPTION_ID, 0L, "1".repeat(64), "nonce", CAST_AT);

        assertThat(firstHash).hasSize(64);
        assertThat(tamperedPreviousHash).hasSize(64);
        assertThat(firstHash).isNotEqualTo(tamperedPreviousHash);
    }

    @Test
    void versionOneHashKeepsTheOriginalPayloadSoExistingLedgersStillVerify() throws Exception {
        String expected = sha256Hex("votetrust-ledger-v1|" + CONTEST_ID + "|" + OPTION_ID + "|0|" + "0".repeat(64)
                + "|nonce|2026-08-07T10:00:00Z");

        String actual = service.calculateHash(
                VoteHashChainService.HASH_VERSION_EXACT_TIMESTAMP, CONTEST_ID, OPTION_ID, 0L, "0".repeat(64), "nonce", CAST_AT);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void hashVersionsUseSeparateDomains() {
        String versionOne = service.calculateHash(
                VoteHashChainService.HASH_VERSION_EXACT_TIMESTAMP, CONTEST_ID, OPTION_ID, 0L, "0".repeat(64), "nonce", CAST_AT);
        String versionTwo = service.calculateHash(
                VoteHashChainService.HASH_VERSION_RECORDING_DAY, CONTEST_ID, OPTION_ID, 0L, "0".repeat(64), "nonce", CAST_AT);

        assertThat(versionOne).isNotEqualTo(versionTwo);
    }

    private String sha256Hex(String payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
