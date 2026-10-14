package io.github.humphreymahlangu.yourvoice.support;

import io.github.humphreymahlangu.yourvoice.security.BlindSignatureService;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

/**
 * The voter's side of the blind-signature protocol, mirroring frontend/src/utils/blindSignature.ts.
 */
public final class BlindBallotClient {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private BlindBallotClient() {
    }

    public static BlindedBallot blind(UUID contestId, String modulus, String publicExponent) {
        BigInteger n = BlindSignatureService.decodeInteger(modulus);
        BigInteger e = BlindSignatureService.decodeInteger(publicExponent);
        byte[] tokenBytes = new byte[32];
        SECURE_RANDOM.nextBytes(tokenBytes);
        String ballotToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        BigInteger blindingFactor;
        do {
            blindingFactor = new BigInteger(n.bitLength(), SECURE_RANDOM);
        } while (blindingFactor.compareTo(BigInteger.ONE) <= 0
                || blindingFactor.compareTo(n) >= 0
                || !blindingFactor.gcd(n).equals(BigInteger.ONE));

        BigInteger hash = BlindSignatureService.fullDomainHash(contestId, ballotToken, n);
        BigInteger blinded = hash.multiply(blindingFactor.modPow(e, n)).mod(n);
        return new BlindedBallot(ballotToken, BlindSignatureService.encodeInteger(blinded), blindingFactor, n);
    }

    public record BlindedBallot(String ballotToken, String blindedToken, BigInteger blindingFactor, BigInteger modulus) {

        public String unblind(String blindSignature) {
            BigInteger signature = BlindSignatureService.decodeInteger(blindSignature)
                    .multiply(blindingFactor.modInverse(modulus))
                    .mod(modulus);
            return BlindSignatureService.encodeInteger(signature);
        }
    }
}
