package io.github.humphreymahlangu.votetrust.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.votetrust.entity.LedgerState;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerPublicationServiceTest {

    private final LedgerPublicationService service = new LedgerPublicationService(null, null, null, null, null);

    @Test
    void publicHashMatchesTheDocumentedFormatSoThirdPartiesCanRecomputeIt() {
        // The same vector is asserted by the frontend's in-browser verifier (ledgerVerification.test.ts).
        String hash = service.calculatePublicHash(
                UUID.fromString("11111111-1111-1111-1111-111111111111"),
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                0,
                LedgerState.GENESIS_HASH,
                "nonce"
        );

        assertThat(hash).isEqualTo("2cdaf1cf60d1147efa190480f29edc33977c19ce377b7053dc8ce4da3c12c78c");
    }
}
