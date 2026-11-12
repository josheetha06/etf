package com.epictalenttrust.hr.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    // RFC 6238 appendix B: secret "12345678901234567890" (ASCII), SHA1, 8 digits at T=59 -> 94287082.
    // With 6 digits the last six digits apply: 287082.
    private static final String RFC_SECRET = Base32.encode("12345678901234567890".getBytes());

    @Test
    void matchesRfc6238TestVector() {
        TotpService totp = new TotpService(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));
        assertThat(totp.currentCode(RFC_SECRET)).isEqualTo("287082");
        assertThat(totp.verify(RFC_SECRET, "287082")).isTrue();
    }

    @Test
    void acceptsOneStepOfClockDriftButNotMore() {
        TotpService at59 = new TotpService(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC));
        String code = at59.currentCode(RFC_SECRET);
        TotpService thirtySecondsLater = new TotpService(Clock.fixed(Instant.ofEpochSecond(89), ZoneOffset.UTC));
        TotpService twoMinutesLater = new TotpService(Clock.fixed(Instant.ofEpochSecond(179), ZoneOffset.UTC));
        assertThat(thirtySecondsLater.verify(RFC_SECRET, code)).isTrue();
        assertThat(twoMinutesLater.verify(RFC_SECRET, code)).isFalse();
    }

    @Test
    void rejectsMalformedCodes() {
        TotpService totp = new TotpService(Clock.systemUTC());
        String secret = totp.newSecret();
        assertThat(totp.verify(secret, null)).isFalse();
        assertThat(totp.verify(secret, "12345")).isFalse();
        assertThat(totp.verify(secret, "abcdef")).isFalse();
    }

    @Test
    void buildsAnAuthenticatorUri() {
        TotpService totp = new TotpService(Clock.systemUTC());
        String uri = totp.otpauthUri("ETT HR portal", "meera@example.com", "ABCDEF");
        assertThat(uri).startsWith("otpauth://totp/ETT%20HR%20portal:meera%40example.com?secret=ABCDEF")
                .contains("issuer=ETT%20HR%20portal").contains("digits=6").contains("period=30");
    }

    @Test
    void base32RoundTrips() {
        byte[] data = {0, 1, 2, 3, (byte) 250, (byte) 251, 99};
        assertThat(Base32.decode(Base32.encode(data))).containsExactly(data);
    }
}
