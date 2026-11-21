package dev.pbroman.brat.core.loader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.pbroman.brat.core.schema.SuiteSchema;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every suite file in the repository, checked against the committed schema as the loader composes it
 * — so anchors and merge keys resolve exactly as they do when the suite is loaded.
 * <p>
 * A file is expected to validate unless it is listed in {@link #EXPECTED_TO_FAIL}, with the location
 * its error must point at. A fixture added to any module is covered without being registered.
 */
class SuiteSchemaFixturesTest {

    /** Fixtures with a shape error, by path from the repository root, and where the schema must report it. */
    private static final Map<String, String> EXPECTED_TO_FAIL = Map.of(
            "brat-cli/src/test/resources/cli/broken.brat.yaml", "/requests/0",
            "brat-junit/src/test/resources/suites/broken/broken.brat.yaml", "/requests/0",
            "brat-integration-tests/src/test/resources/suites/broken/unknown-key.brat.yaml", "/requests/0",
            "brat-integration-tests/src/test/resources/suites/broken/mis-indented-body.brat.yaml",
                    "/requests/0/requestDefinition/headers/body");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Schema schema;

    @BeforeAll
    static void compileSchema() {
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SuiteSchema.json(), InputFormat.JSON);
    }

    private static Path root() {
        var root = System.getProperty("brat.repository.root");
        assertThat(root)
                .as("the system property brat.repository.root, set by surefire")
                .isNotBlank();
        return Path.of(root);
    }

    static Stream<String> suiteFiles() throws IOException {
        var root = root();
        try (var files = Files.walk(root)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".brat.yaml"))
                    .filter(path -> !isBuildOutput(root.relativize(path)))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    /** Whether a path, relative to the repository root, lies under a module's {@code target} directory. */
    private static boolean isBuildOutput(Path relative) {
        for (var element : relative) {
            if ("target".equals(element.toString())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> errorLocations(String relativePath) throws IOException {
        var yaml = Files.readString(root().resolve(relativePath), StandardCharsets.UTF_8);
        var tree = JSON.valueToTree(YamlComposer.compose(yaml, YamlLimits.SUITE).root());
        return schema.validate(tree).stream()
                .map(error -> error.getInstanceLocation().toString())
                .toList();
    }

    @Test
    void suiteFiles_areFound() throws IOException {
        // when
        var files = suiteFiles().toList();

        // then
        assertThat(files).hasSizeGreaterThan(30).containsAll(EXPECTED_TO_FAIL.keySet());
    }

    @ParameterizedTest
    @MethodSource("suiteFiles")
    void suiteFile_validatesUnlessListedAsBroken(String relativePath) throws IOException {
        // when
        var locations = errorLocations(relativePath);

        // then
        var expected = EXPECTED_TO_FAIL.get(relativePath);
        if (expected == null) {
            assertThat(locations).as(relativePath).isEmpty();
        } else {
            assertThat(locations).as(relativePath).containsOnly(expected);
        }
    }
}
