package com.epictalenttrust.hr.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs the whole application against a real PostgreSQL database, because the schema relies on
 * PostgreSQL enums, triggers and session settings that an in-memory database cannot imitate.
 *
 * <p>Set {@code TEST_DATABASE_URL} (plus optional {@code TEST_DATABASE_USER} and
 * {@code TEST_DATABASE_PASSWORD}) to use an existing, disposable database. Otherwise a
 * PostgreSQL 18 container is started, which needs Docker.
 */
@SpringBootTest(properties = {
        "ett.scheduling.jobs-enabled=false",
        "ett.security.encryption-key=dGVzdC1rZXktdGVzdC1rZXktdGVzdC1rZXktdGVzdCE=",
        "server.servlet.session.cookie.secure=false",
        "spring.mail.host="
})
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTest {

    private static PostgreSQLContainer container;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) throws Exception {
        String url = System.getenv("TEST_DATABASE_URL");
        if (url != null && !url.isBlank()) {
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> env("TEST_DATABASE_USER", "ett"));
            registry.add("spring.datasource.password", () -> env("TEST_DATABASE_PASSWORD", "ett"));
        } else {
            if (container == null) {
                container = new PostgreSQLContainer("postgres:18-alpine");
                container.start();
            }
            registry.add("spring.datasource.url", container::getJdbcUrl);
            registry.add("spring.datasource.username", container::getUsername);
            registry.add("spring.datasource.password", container::getPassword);
        }
        Path storage = Files.createTempDirectory("ett-test-files");
        registry.add("ett.storage.local-dir", storage::toString);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
