package io.github.humphreymahlangu.votetrust.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IdentityHashServiceTest {

    @Test
    void hashesIdentityNumberDeterministicallyWithoutReturningRawValue() {
        IdentityHashService service = new IdentityHashService(
                new IdentityHashProperties("identity-hash-test-pepper-that-is-long-enough")
        );

        String firstHash = service.hashSouthAfricanIdNumber("1001015000083");
        String secondHash = service.hashSouthAfricanIdNumber("1001015000083");

        assertThat(firstHash).isEqualTo(secondHash);
        assertThat(firstHash).hasSize(64);
        assertThat(firstHash).doesNotContain("1001015000083");
    }
}
