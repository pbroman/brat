package dev.pbroman.brat.core.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResourceReaderTest {

    @TempDir
    Path tempDir;

    @Test
    void readFileToString_resolvesFilePrefixAgainstFilesystem() throws IOException {
        // given
        var file = tempDir.resolve("greeting.txt");
        Files.writeString(file, "hello from filesystem");

        // when
        var content = ResourceReader.readFileToString("file:" + file);

        // then
        assertThat(content).isEqualTo("hello from filesystem");
    }

    @Test
    void readFileToString_resolvesClasspathPrefixAgainstClassloader() {
        // when
        var content = ResourceReader.readFileToString("classpath:resource-reader/greeting.txt");

        // then
        assertThat(content).isEqualTo("hello from classpath");
    }

    @Test
    void readFileToString_resolvesBarePathAgainstClassloader() {
        // when
        var content = ResourceReader.readFileToString("resource-reader/greeting.txt");

        // then
        assertThat(content).isEqualTo("hello from classpath");
    }

    @Test
    void readFileToString_defaultsToUtf8() {
        // when
        var content = ResourceReader.readFileToString("resource-reader/greeting-utf8.txt");

        // then
        assertThat(content).isEqualTo("café");
    }

    @Test
    void readFileToString_appliesGivenCharset() {
        // when
        var content =
                ResourceReader.readFileToString("resource-reader/greeting-latin1.txt", StandardCharsets.ISO_8859_1);

        // then
        assertThat(content).isEqualTo("café");
    }

    @Test
    void readFileToString_throwsBratExceptionWhenFileMissing() {
        // given
        var missingFile = tempDir.resolve("does-not-exist.txt");

        // then
        assertThatThrownBy(() -> ResourceReader.readFileToString("file:" + missingFile))
                .isInstanceOf(BratException.class);
    }

    @Test
    void readFileToString_throwsBratExceptionWhenClasspathResourceMissing() {
        // then
        assertThatThrownBy(() -> ResourceReader.readFileToString("classpath:resource-reader/does-not-exist.txt"))
                .isInstanceOf(BratException.class);
    }

    @Test
    void readFileToString_throwsBratExceptionForUnsupportedScheme() {
        // then
        assertThatThrownBy(() -> ResourceReader.readFileToString("http://example.com/greeting.txt"))
                .isInstanceOf(BratException.class);
    }

    // --- resolve ---

    @Test
    void resolve_returnsAPrefixedLocationUnchanged() {
        // then - a prefix makes a location absolute in its own medium, whatever the suite's is
        assertThat(ResourceReader.resolve("file:/srv/bodies/order.json", "file:/elsewhere/suite.yaml"))
                .isEqualTo("file:/srv/bodies/order.json");
        assertThat(ResourceReader.resolve("classpath:bodies/order.json", "file:/srv/suite.yaml"))
                .isEqualTo("classpath:bodies/order.json");
    }

    @Test
    void resolve_resolvesABarePathAgainstTheSuitesDirectory() {
        // when
        var resolved = ResourceReader.resolve("bodies/order.json", "file:/srv/suites/orders.yaml");

        // then
        assertThat(resolved).isEqualTo("file:/srv/suites/bodies/order.json");
    }

    @Test
    void resolve_keepsTheSuitesMediumForABarePath() {
        // then - a suite loaded from the classpath resolves its bodies there too, and a bare suite
        // location stays bare, which ResourceReader reads as a classpath lookup
        assertThat(ResourceReader.resolve("bodies/order.json", "classpath:suites/orders.yaml"))
                .isEqualTo("classpath:suites/bodies/order.json");
        assertThat(ResourceReader.resolve("bodies/order.json", "suites/orders.yaml"))
                .isEqualTo("suites/bodies/order.json");
    }

    @Test
    void resolve_returnsTheLocationWhenTheSuiteNamesNoDirectory() {
        // when - a suite location with no '/' is its own directory
        var resolved = ResourceReader.resolve("bodies/order.json", "orders.yaml");

        // then
        assertThat(resolved).isEqualTo("bodies/order.json");
    }

    @Test
    void resolve_treatsALeadingSlashAsRelative() {
        // then - there is no bare spelling for an absolute path; 'file:' is how one is written
        assertThat(ResourceReader.resolve("/etc/passwd", "file:/srv/suites/orders.yaml"))
                .isEqualTo("file:/srv/suites//etc/passwd");
    }

    @Test
    void resolve_throwsWhenABarePathHasNoSuiteLocation() {
        // then - nothing to resolve against, and guessing a working directory would be worse
        assertThatThrownBy(() -> ResourceReader.resolve("bodies/order.json", null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("bodies/order.json");
    }

    @Test
    void resolve_throwsWhenTheLocationIsBlank() {
        assertThatThrownBy(() -> ResourceReader.resolve("  ", "file:/srv/suite.yaml"))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() -> ResourceReader.resolve(null, "file:/srv/suite.yaml"))
                .isInstanceOf(BratException.class);
    }

    // --- exists ---

    @Test
    void exists_isTrueForAResourceThatIsThere() throws IOException {
        // given
        var file = tempDir.resolve("present.txt");
        Files.writeString(file, "here");

        // then
        assertThat(ResourceReader.exists("file:" + file)).isTrue();
        assertThat(ResourceReader.exists("classpath:resource-reader/greeting.txt"))
                .isTrue();
        assertThat(ResourceReader.exists("resource-reader/greeting.txt")).isTrue();
    }

    @Test
    void exists_isFalseForAMissingResource() {
        // then
        assertThat(ResourceReader.exists("file:" + tempDir.resolve("absent.txt")))
                .isFalse();
        assertThat(ResourceReader.exists("classpath:resource-reader/absent.txt"))
                .isFalse();
    }

    @Test
    void exists_isTrueForAnEmptyFile() throws IOException {
        // given - an empty body file is legal, and existence must not be decided by reading
        var file = tempDir.resolve("empty.txt");
        Files.createFile(file);

        // then
        assertThat(ResourceReader.exists("file:" + file)).isTrue();
    }

    @Test
    void exists_isFalseForADirectory() {
        // then - a directory exists but cannot be read as content, which is what the caller means.
        // Both media need saying: a classpath directory yields a stream of its own listing.
        assertThat(ResourceReader.exists("file:" + tempDir)).isFalse();
        assertThat(ResourceReader.exists("classpath:resource-reader")).isFalse();
        assertThat(ResourceReader.exists("resource-reader")).isFalse();
    }

    @Test
    void exists_throwsWhenTheLocationIsBlank() {
        assertThatThrownBy(() -> ResourceReader.exists(" ")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> ResourceReader.exists(null)).isInstanceOf(BratException.class);
    }
}
