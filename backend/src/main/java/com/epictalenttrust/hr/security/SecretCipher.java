package com.epictalenttrust.hr.security;

import com.epictalenttrust.hr.config.EttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** AES-256-GCM encryption for small secrets such as authenticator keys. Output: "v1:" + base64(iv || ciphertext). */
@Component
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    /** Where a generated key is kept: beside the CV files, so the two are backed up together. */
    static final String GENERATED_KEY_FILE = ".encryption-key";

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(EttProperties properties) {
        String encoded = properties.security().encryptionKey();
        if (encoded == null || encoded.isBlank()) {
            encoded = generatedKey(Path.of(properties.storage().localDir()).resolve(GENERATED_KEY_FILE));
        }
        byte[] raw = Base64.getDecoder().decode(encoded.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("ETT_ENCRYPTION_KEY must decode to exactly 32 bytes.");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    /**
     * Without ETT_ENCRYPTION_KEY the portal creates its own key on first start and reuses it afterwards,
     * so a new installation works straight away. Production installations should set the key explicitly.
     */
    static String generatedKey(Path file) {
        try {
            if (Files.exists(file)) {
                log.info("Using the encryption key saved in {} (ETT_ENCRYPTION_KEY is not set)", file);
                return Files.readString(file, StandardCharsets.US_ASCII).strip();
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            String encoded = Base64.getEncoder().encodeToString(raw);
            Files.writeString(file, encoded, StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException notPosix) {
                // Windows: the file keeps the folder's permissions.
            }
            log.warn("ETT_ENCRYPTION_KEY is not set, so a new key was saved in {}. Back it up with the CV files, "
                    + "or set ETT_ENCRYPTION_KEY yourself.", file);
            return encoded;
        } catch (IOException ex) {
            throw new IllegalStateException("ETT_ENCRYPTION_KEY is not set and no key could be saved in " + file, ex);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + sealed.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(sealed, 0, out, iv.length, sealed.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt secret", ex);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Unsupported secret format");
        }
        try {
            byte[] in = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not decrypt secret", ex);
        }
    }
}
