package dev.pbroman.brat.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.pbroman.brat.core.exception.BratException;

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
        var content = ResourceReader.readFileToString("resource-reader/greeting-latin1.txt", StandardCharsets.ISO_8859_1);

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

}
