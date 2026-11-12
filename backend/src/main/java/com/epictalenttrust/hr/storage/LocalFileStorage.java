package com.epictalenttrust.hr.storage;

import com.epictalenttrust.hr.config.EttProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Stores files on a local or mounted disk. Swap for an S3-compatible implementation in cloud deployments. */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(EttProperties properties) {
        this.root = Path.of(properties.storage().localDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory " + root, ex);
        }
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temp = Files.createTempFile(target.getParent(), ".upload", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot store " + key, ex);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot delete " + key, ex);
        }
    }

    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}
