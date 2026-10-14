package io.github.humphreymahlangu.votetrust.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.humphreymahlangu.votetrust.support.BlindBallotClient;
import java.math.BigInteger;
import java.security.interfaces.RSAPrivateKey;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlindSignatureServiceTest {

    private static final UUID CONTEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final BlindSignatureService SERVICE = new BlindSignatureService(
            new VoteCredentialProperties("test-vote-credential-pepper-for-voting-tests-32-chars-minimum")
    );
    private static BlindSignatureService.GeneratedSigningKey key;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    static void generateKey() {
        key = SERVICE.generateSigningKey(CONTEST_ID);
        privateKey = SERVICE.decryptPrivateKey(key.encryptedPrivateKey(), CONTEST_ID);
    }

    @Test
    void unblindedSignatureVerifiesForTheBallotToken() {
        BlindBallotClient.BlindedBallot ballot = BlindBallotClient.blind(CONTEST_ID, key.modulus(), key.publicExponent());

        String signature = ballot.unblind(sign(ballot.blindedToken()));

        assertThat(verify(CONTEST_ID, ballot.ballotToken(), signature)).isTrue();
    }

    @Test
    void signatureDoesNotVerifyForAnotherTokenOrContest() {
        BlindBallotClient.BlindedBallot ballot = BlindBallotClient.blind(CONTEST_ID, key.modulus(), key.publicExponent());
        String signature = ballot.unblind(sign(ballot.blindedToken()));

        assertThat(verify(CONTEST_ID, "A".repeat(43), signature)).isFalse();
        assertThat(verify(UUID.randomUUID(), ballot.ballotToken(), signature)).isFalse();
    }

    @Test
    void blindedTokenRevealsNothingAboutTheSignatureTheVoterLaterPresents() {
        BlindBallotClient.BlindedBallot ballot = BlindBallotClient.blind(CONTEST_ID, key.modulus(), key.publicExponent());
        String blindSignature = sign(ballot.blindedToken());

        String signature = ballot.unblind(blindSignature);

        assertThat(signature).isNotEqualTo(blindSignature);
        assertThat(ballot.blindedToken()).isNotEqualTo(BlindSignatureService.encodeInteger(
                BlindSignatureService.fullDomainHash(CONTEST_ID, ballot.ballotToken(), modulus())));
    }

    @Test
    void privateKeyCannotBeDecryptedForAnotherContest() {
        assertThatThrownBy(() -> SERVICE.decryptPrivateKey(key.encryptedPrivateKey(), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blindedTokensOutsideTheModulusRangeAreRejected() {
        assertThat(SERVICE.isValidBlindedToken(BigInteger.ONE, modulus())).isFalse();
        assertThat(SERVICE.isValidBlindedToken(modulus(), modulus())).isFalse();
        assertThat(SERVICE.isValidBlindedToken(BigInteger.TWO, modulus())).isTrue();
    }

    @Test
    void fullDomainHashMatchesTheSharedTestVector() throws Exception {
        // Same vector as frontend/src/utils/blindSignature.test.ts, computed independently in Python.
        BigInteger modulus = BigInteger.TWO.pow(2048).subtract(BigInteger.valueOf(159));

        BigInteger hash = BlindSignatureService.fullDomainHash(
                CONTEST_ID,
                "q8Xb0Hw2Lr5nKcTzW1sYd9aEoP3uJvMfGtBiN7xR4lA",
                modulus
        );

        assertThat(hash.toString(16)).startsWith("70b6c050203b515ee756494d4b9e4c7bb8f9063e8264e6bc1502870aee2ae3ea");
        assertThat(BlindSignatureService.fingerprint(hash))
                .isEqualTo("431887e9e599b9f61fc58b11bcf064381b9048705b1e50464a095d7aadd8a51a");
    }

    private static String sign(String blindedToken) {
        return BlindSignatureService.encodeInteger(
                SERVICE.signBlindedToken(BlindSignatureService.decodeInteger(blindedToken), privateKey));
    }

    private static boolean verify(UUID contestId, String ballotToken, String signature) {
        return SERVICE.verify(
                contestId,
                ballotToken,
                BlindSignatureService.decodeInteger(signature),
                modulus(),
                BlindSignatureService.decodeInteger(key.publicExponent())
        );
    }

    private static BigInteger modulus() {
        return BlindSignatureService.decodeInteger(key.modulus());
    }
}
