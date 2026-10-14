package io.github.humphreymahlangu.votetrust.security;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class IdentityHashService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SecretKeySpec secretKeySpec;

    public IdentityHashService(IdentityHashProperties properties) {
        this.secretKeySpec = new SecretKeySpec(
                properties.identityHashPepper().getBytes(StandardCharsets.UTF_8),
                HMAC_ALGORITHM
        );
    }

    public String hashSouthAfricanIdNumber(String normalizedIdNumber) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            return HexFormat.of().formatHex(mac.doFinal(normalizedIdNumber.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash identity number", exception);
        }
    }
}
