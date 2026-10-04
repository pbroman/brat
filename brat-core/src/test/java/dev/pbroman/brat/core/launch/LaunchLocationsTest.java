package dev.pbroman.brat.core.launch;

import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LaunchLocationsTest {

    private static final Path WORKING_DIRECTORY = Path.of("").toAbsolutePath();

    @Test
    void fileLocation_readsABarePathAsRelativeToTheWorkingDirectory() {
        // when
        var result = LaunchLocations.fileLocation("orders-api/orders.brat.yaml");

        // then - not a classpath lookup, which is what a bare path means further down
        assertThat(result).isEqualTo("file:" + WORKING_DIRECTORY.resolve("orders-api/orders.brat.yaml"));
    }

    @Test
    void fileLocation_keepsAnAbsolutePath() {
        // given
        var absolute = WORKING_DIRECTORY.resolve("suites/orders.brat.yaml").toString();

        // when
        var result = LaunchLocations.fileLocation(absolute);

        // then
        assertThat(result).isEqualTo("file:" + absolute);
    }

    @Test
    void fileLocation_readsAFilePrefixedPathAsAFilesystemPath() {
        // when
        var result = LaunchLocations.fileLocation("file:orders.brat.yaml");

        // then - the prefix is allowed, never needed, and a relative path behind it is still made absolute
        assertThat(result).isEqualTo("file:" + WORKING_DIRECTORY.resolve("orders.brat.yaml"));
    }

    @Test
    void fileLocation_normalisesThePath() {
        // when
        var result = LaunchLocations.fileLocation("a/../b/./orders.brat.yaml");

        // then
        assertThat(result).isEqualTo("file:" + WORKING_DIRECTORY.resolve("b/orders.brat.yaml"));
    }

    @Test
    void fileLocation_keepsAClasspathLocationUnchanged() {
        // when
        var result = LaunchLocations.fileLocation("classpath:suites/orders.brat.yaml");

        // then
        assertThat(result).isEqualTo("classpath:suites/orders.brat.yaml");
    }

    @Test
    void fileLocation_doesNotRequireTheFileToExist() {
        // when
        var result = LaunchLocations.fileLocation("nowhere/at/all.brat.yaml");

        // then
        assertThat(result).startsWith("file:").endsWith("nowhere/at/all.brat.yaml");
    }

    @Test
    void fileLocation_doesNotExpandATilde() {
        // when
        var result = LaunchLocations.fileLocation("~/orders.brat.yaml");

        // then
        assertThat(result).isEqualTo("file:" + WORKING_DIRECTORY.resolve("~/orders.brat.yaml"));
    }

    @Test
    void fileLocation_throwsForNullOrBlank() {
        // then
        assertThatThrownBy(() -> LaunchLocations.fileLocation(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> LaunchLocations.fileLocation(" ")).isInstanceOf(BratException.class);
    }

    @Test
    void fileLocation_throwsForAFilePrefixWithNothingAfterIt() {
        // then
        assertThatThrownBy(() -> LaunchLocations.fileLocation("file:")).isInstanceOf(BratException.class);
    }

    @Test
    void fileLocation_throwsForAnInvalidPath() {
        // then - a NUL character is not a path on any filesystem
        assertThatThrownBy(() -> LaunchLocations.fileLocation("bad\0name")).isInstanceOf(BratException.class);
    }

    @Test
    void directory_readsABarePathAsRelativeToTheWorkingDirectory() {
        // when
        var result = LaunchLocations.directory("orders-api/dev");

        // then
        assertThat(result).isEqualTo(WORKING_DIRECTORY.resolve("orders-api/dev"));
    }

    @Test
    void directory_acceptsAFilePrefixAndNormalises() {
        // when
        var result = LaunchLocations.directory("file:orders-api/x/../dev");

        // then
        assertThat(result).isEqualTo(WORKING_DIRECTORY.resolve("orders-api/dev"));
    }

    @Test
    void directory_keepsAnAbsolutePath() {
        // given
        var absolute = WORKING_DIRECTORY.resolve("orders-api/dev");

        // when
        var result = LaunchLocations.directory(absolute.toString());

        // then
        assertThat(result).isEqualTo(absolute);
    }

    @Test
    void directory_rejectsAClasspathDirectorySayingItMustBeOnTheFilesystem() {
        // then
        assertThatThrownBy(() -> LaunchLocations.directory("classpath:envs/dev"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("classpath:envs/dev")
                .hasMessageContaining("filesystem");
    }

    @Test
    void directory_throwsForNullBlankOrAnInvalidPath() {
        // then
        assertThatThrownBy(() -> LaunchLocations.directory(null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> LaunchLocations.directory("")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> LaunchLocations.directory("file:")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> LaunchLocations.directory("bad\0dir")).isInstanceOf(BratException.class);
    }
}
