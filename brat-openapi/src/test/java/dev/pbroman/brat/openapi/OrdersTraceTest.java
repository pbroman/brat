package dev.pbroman.brat.openapi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The walkthrough of {@code brat import} for suite authors, as a test: its spec,
 * {@code openapi/orders-openapi.yaml}, imports to exactly the files under {@code golden/orders-api/}.
 * <p>
 * A change to the import's output changes those files on purpose, and the walkthrough with them.
 */
class OrdersTraceTest {

    private static Path resource(String name) throws URISyntaxException {
        return Path.of(
                Objects.requireNonNull(OrdersTraceTest.class.getResource(name)).toURI());
    }

    private static Scaffold imported() throws IOException, URISyntaxException {
        var spec = Files.readString(resource("/openapi/orders-openapi.yaml"), StandardCharsets.UTF_8);
        return OpenApiImporter.scaffold(spec, "orders-openapi.yaml", ImportOptions.defaults());
    }

    @Test
    void scaffold_writesExactlyTheGoldenFiles() throws IOException, URISyntaxException {
        // given
        var golden = resource("/golden/orders-api");

        // when
        var scaffold = imported();

        // then
        try (var files = Files.walk(golden)) {
            var expected = files.filter(Files::isRegularFile)
                    .map(path -> golden.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
            assertThat(scaffold.files().keySet().stream().sorted().toList()).isEqualTo(expected);
        }
        for (var file : scaffold.files().entrySet()) {
            var expected = Files.readString(golden.resolve(file.getKey()), StandardCharsets.UTF_8);
            assertThat(file.getValue()).as(file.getKey()).isEqualTo(expected);
        }
    }

    @Test
    void scaffold_suggestsTheDirectoryAndCountsOfTheWalkthrough() throws IOException, URISyntaxException {
        // when
        var scaffold = imported();

        // then
        assertThat(scaffold.directory()).isEqualTo("orders-api");
        assertThat(scaffold.environments()).containsExactly("local", "staging-server-reset-nightly");
        assertThat(scaffold.requests()).isEqualTo(9);
        assertThat(scaffold.subSuites()).isEqualTo(2);
        assertThat(scaffold.setupRequests()).isEqualTo(1);
    }
}
