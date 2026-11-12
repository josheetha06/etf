package com.epictalenttrust.hr.portable;

import io.zonky.test.db.postgres.embedded.PgBinaryResolver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

/** Reads the PostgreSQL archive shipped in the package's "postgres" folder. */
final class BundledPostgres implements PgBinaryResolver {

    private final Path folder;

    BundledPostgres(Path folder) {
        this.folder = folder;
    }

    @Override
    public InputStream getPgBinary(String system, String machineHardware) throws IOException {
        String os = system.toLowerCase(Locale.ROOT);
        Path exact = folder.resolve("postgres-" + os + "-" + machineHardware + ".txz");
        if (Files.isRegularFile(exact)) {
            return Files.newInputStream(exact);
        }
        // The macOS archive is universal: it runs on both Intel and Apple silicon.
        if (os.equals("darwin") && Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                Path any = files.filter(f -> f.getFileName().toString().startsWith("postgres-darwin-")).findFirst().orElse(null);
                if (any != null) {
                    return Files.newInputStream(any);
                }
            }
        }
        throw new IOException("This download doesn't include the database for " + system + " (" + machineHardware
                + "). Download the ETT HR portal package made for this computer.");
    }
}
