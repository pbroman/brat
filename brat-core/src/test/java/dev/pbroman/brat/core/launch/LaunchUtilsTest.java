package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LaunchUtilsTest {

    @TempDir
    Path dir;

    // ---------- checkDirectory ----------

    @Test
    void checkDirectory_acceptsADirectory() {
        // then
        assertThatCode(() -> LaunchUtils.checkDirectory(dir, "dev", "environment"))
                .doesNotThrowAnyException();
    }

    @Test
    void checkDirectory_rejectsAMissingDirectoryNamingTheKindAndTheDirectory() {
        // given
        var missing = dir.resolve("prod");

        // then
        assertThatThrownBy(() -> LaunchUtils.checkDirectory(missing, "prod", "environment"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("environment directory 'prod'")
                .hasMessageContaining("does not exist")
                .hasMessageContaining(missing.toString());
    }

    @Test
    void checkDirectory_rejectsAFileNamingTheKindAndTheDirectory() throws IOException {
        // given
        var file = Files.writeString(dir.resolve("orders.brat.yaml"), "name: orders\n");

        // then
        assertThatThrownBy(() -> LaunchUtils.checkDirectory(file, "orders.brat.yaml", "suite"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("suite directory 'orders.brat.yaml'")
                .hasMessageContaining("is not a directory");
    }

    // ---------- regularFilesIn ----------

    @Test
    void regularFilesIn_sortsByNameComparedAsStrings() throws IOException {
        // given - byte order, never locale: uppercase before lowercase, '.' before a digit
        for (var name : new String[] {"secrets001.yaml", "b.yaml", "secrets.yaml", "B.yaml", "a.yaml"}) {
            Files.writeString(dir.resolve(name), "k: v\n");
        }

        // when
        var result = LaunchUtils.regularFilesIn(dir, "dev", "environment");

        // then
        assertThat(result)
                .extracting(file -> file.getFileName().toString())
                .containsExactly("B.yaml", "a.yaml", "b.yaml", "secrets.yaml", "secrets001.yaml");
    }

    @Test
    void regularFilesIn_neitherListsNorEntersSubdirectories() throws IOException {
        // given - a directory named like a file, and a file one level down
        Files.writeString(dir.resolve("env.yaml"), "k: v\n");
        Files.createDirectory(dir.resolve("secrets001.yaml"));
        Files.createDirectories(dir.resolve("nested"));
        Files.writeString(dir.resolve("nested/orders.brat.yaml"), "name: orders\n");

        // when
        var result = LaunchUtils.regularFilesIn(dir, "dev", "environment");

        // then
        assertThat(result).containsExactly(dir.resolve("env.yaml"));
    }

    @Test
    void regularFilesIn_returnsNothingForAnEmptyDirectory() {
        // then
        assertThat(LaunchUtils.regularFilesIn(dir, "dev", "environment")).isEmpty();
    }

    @Test
    void regularFilesIn_rejectsWhatCheckDirectoryRejects() {
        // then
        assertThatThrownBy(() -> LaunchUtils.regularFilesIn(dir.resolve("prod"), "prod", "environment"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("environment directory 'prod'");
    }

    @Test
    void regularFilesIn_rejectsADirectoryThatCannotBeListedNamingTheKindAndTheDirectory() throws IOException {
        // given - a directory nobody may read; meaningless on a filesystem without POSIX permissions, or as root
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        var locked = Files.createDirectory(dir.resolve("locked"));
        Files.setPosixFilePermissions(locked, Set.of());
        assumeFalse(Files.isReadable(locked), "running with permission to read any directory");

        try {
            // then
            assertThatThrownBy(() -> LaunchUtils.regularFilesIn(locked, "locked", "suite"))
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("suite directory 'locked'")
                    .hasMessageContaining("cannot be listed");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }
}
