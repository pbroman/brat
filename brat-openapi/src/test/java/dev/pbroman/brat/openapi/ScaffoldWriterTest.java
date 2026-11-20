package dev.pbroman.brat.openapi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScaffoldWriterTest {

    @TempDir
    private Path temp;

    private static Scaffold scaffold(Map<String, String> files) {
        return new Scaffold(
                "orders-api", files.keySet().iterator().next(), files, List.of("local"), 1, 0, 0, List.of());
    }

    private static Scaffold ordersScaffold() {
        var files = new LinkedHashMap<String, String>();
        files.put("orders-api.brat.yaml", "name: Bestellungen für Müller\n");
        files.put("bodies/import-orders.json", "{}\n");
        files.put("local/env.yaml", "baseUrl: http://localhost:8080\n");
        return scaffold(files);
    }

    private static List<String> contentsOf(Path directory) throws IOException {
        try (var files = Files.walk(directory)) {
            return files.map(path -> directory.relativize(path).toString().replace('\\', '/'))
                    .filter(name -> !name.isEmpty())
                    .sorted()
                    .toList();
        }
    }

    @Test
    void write_createsTheDirectoryItsParentsAndEveryFileAsUtf8() throws IOException {
        // given
        var directory = temp.resolve("work/orders-api");

        // when
        ScaffoldWriter.write(ordersScaffold(), directory);

        // then
        assertThat(contentsOf(directory))
                .containsExactly(
                        "bodies", "bodies/import-orders.json", "local", "local/env.yaml", "orders-api.brat.yaml");
        assertThat(Files.readString(directory.resolve("orders-api.brat.yaml"), StandardCharsets.UTF_8))
                .isEqualTo("name: Bestellungen für Müller\n");
        assertThat(Files.readString(directory.resolve("local/env.yaml"), StandardCharsets.UTF_8))
                .isEqualTo("baseUrl: http://localhost:8080\n");
    }

    @Test
    void write_returnsThePathsWrittenInTheScaffoldsOrder() {
        // given
        var directory = temp.resolve("orders-api");

        // when
        var written = ScaffoldWriter.write(ordersScaffold(), directory);

        // then
        assertThat(written)
                .containsExactly(
                        directory.resolve("orders-api.brat.yaml"),
                        directory.resolve("bodies/import-orders.json"),
                        directory.resolve("local/env.yaml"));
    }

    @Test
    void write_acceptsAnEmptyDirectory() throws IOException {
        // given
        var directory = Files.createDirectory(temp.resolve("orders-api"));

        // when
        ScaffoldWriter.write(ordersScaffold(), directory);

        // then
        assertThat(directory.resolve("orders-api.brat.yaml")).exists();
    }

    @Test
    void write_refusesADirectoryHoldingAnythingNamingItAndWritingNothing() throws IOException {
        // given
        var directory = Files.createDirectory(temp.resolve("orders-api"));
        Files.writeString(directory.resolve("notes.txt"), "mine");
        Files.createDirectory(directory.resolve("local"));

        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(ordersScaffold(), directory))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(directory.toString())
                .hasMessageContaining("local")
                .hasMessageContaining("notes.txt");
        assertThat(contentsOf(directory)).containsExactly("local", "notes.txt");
    }

    @Test
    void write_namesAtMostFiveEntriesAndHowManyMore() throws IOException {
        // given
        var directory = Files.createDirectory(temp.resolve("orders-api"));
        for (var name : List.of("g", "b", "f", "a", "c", "e", "d")) {
            Files.writeString(directory.resolve(name), "");
        }

        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(ordersScaffold(), directory))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("a, b, c, d, e")
                .hasMessageContaining("2 more")
                .hasMessageNotContaining("f,")
                .hasMessageNotContaining(", g");
    }

    @Test
    void write_refusesAFileInPlaceOfTheDirectory() throws IOException {
        // given
        var file = Files.writeString(temp.resolve("orders-api"), "a file");

        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(ordersScaffold(), file))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(file.toString());
        assertThat(Files.readString(file)).isEqualTo("a file");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", ".", "local/..", "../outside.txt", "local/../../outside.txt", "/tmp/absolute.txt", "a\u0000b"
            })
    void write_refusesAPathThatIsEmptyAbsoluteOrLeavesTheDirectoryAndWritesNothing(String path) throws IOException {
        // given
        var files = new LinkedHashMap<String, String>();
        files.put("orders-api.brat.yaml", "name: x\n");
        files.put(path, "escaped\n");
        var directory = temp.resolve("work/orders-api");

        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(scaffold(files), directory))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'" + path + "'");
        assertThat(contentsOf(temp)).isEmpty();
    }

    @Test
    void write_namesTheFileItCannotWriteAndKeepsThoseBefore() {
        // given
        var files = new LinkedHashMap<String, String>();
        files.put("orders-api.brat.yaml", "name: x\n");
        files.put("local", "a file where a directory is needed\n");
        files.put("local/env.yaml", "baseUrl: \"\"\n");
        var directory = temp.resolve("orders-api");

        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(scaffold(files), directory))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'local/env.yaml'")
                .hasMessageContaining(directory.resolve("local") + " is already there");
        assertThat(directory.resolve("orders-api.brat.yaml")).exists();
        assertThat(directory.resolve("local")).isRegularFile();
    }

    @Test
    void write_refusesANullScaffold() {
        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(null, temp.resolve("x"))).isInstanceOf(BratException.class);
    }

    @Test
    void write_refusesANullDirectory() {
        // when / then
        assertThatThrownBy(() -> ScaffoldWriter.write(ordersScaffold(), null)).isInstanceOf(BratException.class);
    }
}
