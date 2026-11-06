package com.example.paygenix.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A migration that ran somewhere must never change: Flyway refuses to start on a checksum mismatch, and tests on a fresh
 * database would not notice. New migrations are appended to src/test/resources/migration-checksums.txt
 * ("file sha256", one per line); an edit to a listed one fails here.
 */
class MigrationImmutabilityTest {

    private static final Path DIR = Path.of("src/main/resources/db/migration");

    @Test
    void appliedMigrationsAreUnchanged() throws Exception {
        List<String> lines = Files.readAllLines(Path.of("src/test/resources/migration-checksums.txt"));
        for (String line : lines) {
            String[] parts = line.split(" ");
            // line endings do not count (a Windows checkout may have CRLF; Flyway ignores them too)
            byte[] content = Files.readString(DIR.resolve(parts[0])).replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            assertThat(sha).as("%s changed after it was released; add a new migration instead", parts[0]).isEqualTo(parts[1]);
        }
        try (var files = Files.list(DIR)) {
            assertThat(files.map(f -> f.getFileName().toString()).toList())
                    .as("every migration is listed in migration-checksums.txt")
                    .containsExactlyInAnyOrderElementsOf(lines.stream().map(l -> l.split(" ")[0]).toList());
        }
    }
}
