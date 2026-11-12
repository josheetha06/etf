package com.epictalenttrust.hr.portable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.epictalenttrust.hr.EttHrPortalApplication;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

/**
 * Starts the portal on this computer with its own PostgreSQL database, for evaluation and demos.
 * Everything it stores stays in the "data" folder next to the application.
 *
 * <p>Options: {@code --no-demo} starts without sample data; {@code --no-browser} doesn't open a browser.
 * The system property {@code ett.home} moves the application folder.
 */
public final class PortableLauncher {

    private static final int FIRST_PORT = 8080;

    private PortableLauncher() {
    }

    public static void main(String[] args) throws Exception {
        quietLogging();
        boolean demo = !Arrays.asList(args).contains("--no-demo");
        boolean browser = !Arrays.asList(args).contains("--no-browser");
        Path home = applicationFolder();
        Path data = home.resolve("data");
        Files.createDirectories(data.resolve("logs"));
        int port = freePort();
        String address = "http://localhost:" + port;

        say("Starting the ETT HR portal. The first start takes up to a minute.");
        EmbeddedPostgres database = EmbeddedPostgres.builder()
                .setDataDirectory(data.resolve("database"))
                .setCleanDataDirectory(false)
                .setPgBinaryResolver(new BundledPostgres(home.resolve("postgres")))
                .setServerConfig("listen_addresses", "localhost")
                .setPGStartupWait(Duration.ofSeconds(90))
                .start();

        System.setProperty("spring.datasource.url", database.getJdbcUrl("postgres", "postgres"));
        System.setProperty("spring.datasource.username", "postgres");
        System.setProperty("spring.datasource.password", "");
        System.setProperty("server.port", String.valueOf(port));
        System.setProperty("server.servlet.session.cookie.secure", "false");
        System.setProperty("ett.portal-url", address);
        System.setProperty("ett.storage.local-dir", data.resolve("files").toString());
        System.setProperty("ett.security.encryption-key", encryptionKey(data.resolve("encryption.key")));
        System.setProperty("logging.file.name", data.resolve("logs").resolve("ett-hr-portal.log").toString());

        SpringApplication application = new SpringApplication(EttHrPortalApplication.class);
        application.setAdditionalProfiles(demo ? new String[] {"portable", "demo"} : new String[] {"portable"});
        application.setRegisterShutdownHook(false);
        ConfigurableApplicationContext context;
        try {
            context = application.run(args);
        } catch (RuntimeException e) {
            database.close();
            throw e;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            context.close();
            try {
                database.close();
            } catch (IOException ignored) {
                // The database stops with this process either way.
            }
        }, "ett-shutdown"));

        say("");
        say("The ETT HR portal is running at " + address);
        if (demo) {
            say("Sample sign-in: charlotte.hughes@pellhamrowe.example   Password: Welcome@2026!");
        }
        say("Keep this window open while you use the portal. Close it to stop the portal.");
        say("Your data is kept in " + data);
        if (browser) {
            openBrowser(address);
        }
    }

    /** Keeps the console to short status lines until Spring Boot applies application-portable.yml. */
    private static void quietLogging() {
        Logger root = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.WARN);
    }

    /** The folder holding the application jar, unless ett.home says otherwise. */
    static Path applicationFolder() {
        String configured = System.getProperty("ett.home");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        String jar = System.getProperty("java.class.path").split(File.pathSeparator)[0];
        Path parent = Path.of(jar).toAbsolutePath().getParent();
        return parent != null ? parent : Path.of("").toAbsolutePath();
    }

    private static int freePort() throws IOException {
        for (int port = FIRST_PORT; port < FIRST_PORT + 20; port++) {
            try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
                return port;
            } catch (IOException taken) {
                // Try the next one.
            }
        }
        throw new IOException("Ports " + FIRST_PORT + " to " + (FIRST_PORT + 19) + " are all in use. Close another program and try again.");
    }

    /** Creates this installation's own key on first start and reuses it afterwards. */
    static String encryptionKey(Path file) throws IOException {
        if (Files.exists(file)) {
            return Files.readString(file, StandardCharsets.US_ASCII).strip();
        }
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String encoded = Base64.getEncoder().encodeToString(key);
        Files.writeString(file, encoded, StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException notPosix) {
            // Windows: the file inherits the user's folder permissions.
        }
        return encoded;
    }

    private static void openBrowser(String address) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] command = os.contains("win") ? new String[] {"rundll32", "url.dll,FileProtocolHandler", address}
                : os.contains("mac") ? new String[] {"open", address}
                : new String[] {"xdg-open", address};
        try {
            new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        } catch (IOException e) {
            say("Open " + address + " in your browser.");
        }
    }

    private static void say(String line) {
        System.out.println(line);
    }
}
