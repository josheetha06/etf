package com.epictalenttrust.hr.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;

/** Time-based one-time passwords (RFC 6238): 6 digits, 30-second steps, HMAC-SHA1. */
@Component
public class TotpService {

    private static final int DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TotpService(Clock clock) {
        this.clock = clock;
    }

    public String newSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return Base32.encode(bytes);
    }

    public String otpauthUri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return false;
        }
        long step = clock.instant().getEpochSecond() / STEP_SECONDS;
        byte[] key = Base32.decode(secret);
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            String expected = codeAt(key, step + drift);
            if (MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), code.getBytes(StandardCharsets.US_ASCII))) {
                return true;
            }
        }
        return false;
    }

    /** The code for the current time step; used by tests and the demo login helper. */
    public String currentCode(String secret) {
        return codeAt(Base32.decode(secret), clock.instant().getEpochSecond() / STEP_SECONDS);
    }

    private static String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA1 is not available", ex);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
