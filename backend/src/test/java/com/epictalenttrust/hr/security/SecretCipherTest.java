package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.config.EttProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private static EttProperties withKey(String key) {
        return withKey(key, Path.of("./data"));
    }

    private static EttProperties withKey(String key, Path storage) {
        return new EttProperties("http://localhost", new EttProperties.Security(key, "ETT", 5, 15, true),
                new EttProperties.Storage(storage.toString()), new EttProperties.Mail("x@example.com", "#0a7476", "#0b2545"),
                new EttProperties.Retention(180), new EttProperties.Scheduling(false));
    }

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptsAndDecrypts() {
        SecretCipher cipher = new SecretCipher(withKey(KEY));
        String sealed = cipher.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(sealed).startsWith("v1:").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(sealed)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void usesAFreshIvEveryTime() {
        SecretCipher cipher = new SecretCipher(withKey(KEY));
        assertThat(cipher.encrypt("same")).isNotEqualTo(cipher.encrypt("same"));
    }

    @Test
    void refusesAKeyOfTheWrongLength() {
        assertThatThrownBy(() -> new SecretCipher(withKey(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createsAndKeepsItsOwnKeyWhenNoneIsSet(@TempDir Path storage) {
        String sealed = new SecretCipher(withKey("", storage)).encrypt("JBSWY3DPEHPK3PXP");
        assertThat(storage.resolve(SecretCipher.GENERATED_KEY_FILE)).exists();
        assertThat(new SecretCipher(withKey(" ", storage)).decrypt(sealed)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void detectsTampering() {
        SecretCipher cipher = new SecretCipher(withKey(KEY));
        String sealed = cipher.encrypt("secret");
        String tampered = sealed.substring(0, sealed.length() - 2) + (sealed.endsWith("A") ? "B" : "A") + "=";
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(RuntimeException.class);
    }
}
