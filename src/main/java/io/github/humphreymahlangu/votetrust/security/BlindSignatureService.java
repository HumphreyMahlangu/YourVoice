package io.github.humphreymahlangu.votetrust.security;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * RSA blind signatures with a full-domain hash (Chaum), used to issue ballot credentials the server cannot link to
 * the voter who requested them.
 *
 * <ol>
 *   <li>The voter picks a random ballot token {@code t}, computes {@code H(t)} and blinds it:
 *       {@code b = H(t) * r^e mod n} for a random {@code r}.</li>
 *   <li>After the eligibility checks, the server signs the blinded value: {@code s' = b^d mod n}.</li>
 *   <li>The voter unblinds: {@code s = s' * r^-1 mod n}, which equals {@code H(t)^d mod n}.</li>
 *   <li>The ballot carries {@code (t, s)}. The server checks {@code s^e mod n == H(t)} without learning which
 *       blinded value it came from.</li>
 * </ol>
 *
 * <p>{@code H(t) = MGF1-SHA256("votetrust-ballot-token-v1|contestId|t", modulusBytes + 16) mod n}. The extra 16 bytes
 * make the reduction modulo {@code n} statistically uniform. Integers travel as unpadded base64url big-endian bytes.
 */
@Service
public class BlindSignatureService {

    public static final String TOKEN_DOMAIN = "votetrust-ballot-token-v1";

    private static final int KEY_SIZE_BITS = 2048;
    private static final int HASH_EXTRA_BYTES = 16;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final String KEY_ENCRYPTION_LABEL = "votetrust-ballot-signing-key-encryption-v1";

    private final SecretKeySpec keyEncryptionKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public BlindSignatureService(VoteCredentialProperties properties) {
        // Derive a dedicated AES key from the pepper so the pepper itself is never used as an encryption key.
        byte[] derivedKey = hmacSha256(
                properties.voteCredentialPepper().getBytes(StandardCharsets.UTF_8),
                KEY_ENCRYPTION_LABEL.getBytes(StandardCharsets.UTF_8)
        );
        this.keyEncryptionKey = new SecretKeySpec(derivedKey, "AES");
    }

    public GeneratedSigningKey generateSigningKey(UUID contestId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(KEY_SIZE_BITS, RSAKeyGenParameterSpec.F4), secureRandom);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            return new GeneratedSigningKey(
                    encodeInteger(publicKey.getModulus()),
                    encodeInteger(publicKey.getPublicExponent()),
                    encryptPrivateKey(keyPair.getPrivate().getEncoded(), contestId)
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not generate ballot signing key", exception);
        }
    }

    public RSAPrivateKey decryptPrivateKey(String encryptedPrivateKey, UUID contestId) {
        try {
            byte[] payload = Base64.getDecoder().decode(encryptedPrivateKey);
            byte[] iv = Arrays.copyOfRange(payload, 0, GCM_IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, GCM_IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keyEncryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(contestId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] pkcs8 = cipher.doFinal(ciphertext);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Could not decrypt ballot signing key", exception);
        }
    }

    /** True when the value can be signed: {@code 1 < blinded < n}. */
    public boolean isValidBlindedToken(BigInteger blindedToken, BigInteger modulus) {
        return blindedToken.compareTo(BigInteger.ONE) > 0 && blindedToken.compareTo(modulus) < 0;
    }

    /** Signs a blinded token with the contest's private key. Callers must check {@link #isValidBlindedToken} first. */
    public BigInteger signBlindedToken(BigInteger blindedToken, RSAPrivateKey privateKey) {
        try {
            int modulusBytes = byteLength(privateKey.getModulus());
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            return new BigInteger(1, cipher.doFinal(toFixedLength(blindedToken, modulusBytes)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign blinded ballot token", exception);
        }
    }

    public boolean verify(UUID contestId, String ballotToken, BigInteger signature, BigInteger modulus, BigInteger exponent) {
        if (signature.signum() <= 0 || signature.compareTo(modulus) >= 0) {
            return false;
        }
        BigInteger expected = fullDomainHash(contestId, ballotToken, modulus);
        return MessageDigest.isEqual(
                toFixedLength(signature.modPow(exponent, modulus), byteLength(modulus)),
                toFixedLength(expected, byteLength(modulus))
        );
    }

    public static BigInteger fullDomainHash(UUID contestId, String ballotToken, BigInteger modulus) {
        byte[] seed = String.join("|", TOKEN_DOMAIN, contestId.toString(), ballotToken).getBytes(StandardCharsets.UTF_8);
        byte[] expanded = mgf1Sha256(seed, byteLength(modulus) + HASH_EXTRA_BYTES);
        return new BigInteger(1, expanded).mod(modulus);
    }

    /** SHA-256 of the modulus bytes, shown to voters so they can confirm everyone uses the same contest key. */
    public static String fingerprint(BigInteger modulus) {
        return HexFormat.of().formatHex(sha256(toFixedLength(modulus, byteLength(modulus))));
    }

    public static String encodeInteger(BigInteger value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toFixedLength(value, byteLength(value)));
    }

    public static BigInteger decodeInteger(String value) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(value));
    }

    private String encryptPrivateKey(byte[] pkcs8, UUID contestId) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, keyEncryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
        cipher.updateAAD(contestId.toString().getBytes(StandardCharsets.UTF_8));
        byte[] ciphertext = cipher.doFinal(pkcs8);
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                .put(iv)
                .put(ciphertext)
                .array());
    }

    private static byte[] mgf1Sha256(byte[] seed, int length) {
        byte[] output = new byte[length];
        int offset = 0;
        for (int counter = 0; offset < length; counter++) {
            byte[] block = sha256(ByteBuffer.allocate(seed.length + 4).put(seed).putInt(counter).array());
            int copied = Math.min(block.length, length - offset);
            System.arraycopy(block, 0, output, offset, copied);
            offset += copied;
        }
        return output;
    }

    private static int byteLength(BigInteger value) {
        return Math.max(1, (value.bitLength() + 7) / 8);
    }

    private static byte[] toFixedLength(BigInteger value, int length) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == length) {
            return bytes;
        }
        byte[] fixed = new byte[length];
        if (bytes.length > length) {
            // BigInteger.toByteArray adds a leading zero sign byte for values whose top bit is set.
            System.arraycopy(bytes, bytes.length - length, fixed, 0, length);
        } else {
            System.arraycopy(bytes, 0, fixed, length - bytes.length, bytes.length);
        }
        return fixed;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static byte[] hmacSha256(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    public record GeneratedSigningKey(String modulus, String publicExponent, String encryptedPrivateKey) {
    }
}
