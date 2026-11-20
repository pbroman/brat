package dev.pbroman.brat.openapi;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.pbroman.brat.core.loader.SuiteLoader;
import dev.pbroman.brat.core.schema.SuiteSchema;
import dev.pbroman.brat.core.secrets.FlatYamlLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Every spec under {@code openapi/} in the test resources, imported, and every file of the result
 * checked as BRAT reads it: the suite loads and validates against the suite schema, each environment file
 * loads, and each body file is JSON. A spec added there is covered without being registered.
 */
class ImportedSuitesTest {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static Schema schema;

    @BeforeAll
    static void compileSchema() {
        schema = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(SuiteSchema.json(), InputFormat.JSON);
    }

    static Stream<String> specs() throws IOException, URISyntaxException {
        var directory = Path.of(Objects.requireNonNull(ImportedSuitesTest.class.getResource("/openapi"))
                .toURI());
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList().stream();
        }
    }

    private static Scaffold imported(String spec) throws IOException {
        try (var in = ImportedSuitesTest.class.getResourceAsStream("/openapi/" + spec)) {
            var content = new String(Objects.requireNonNull(in).readAllBytes(), StandardCharsets.UTF_8);
            return OpenApiImporter.scaffold(content, spec, ImportOptions.defaults());
        }
    }

    @Test
    void specs_areFound() throws IOException, URISyntaxException {
        // when
        var specs = specs().toList();

        // then
        assertThat(specs)
                .contains(
                        "orders-openapi.yaml",
                        "awkward-names.yaml",
                        "bodies.json",
                        "no-servers.yaml",
                        "auth-kinds.yaml");
    }

    @ParameterizedTest
    @MethodSource("specs")
    void suite_loads(String spec) throws IOException {
        // given
        var scaffold = imported(spec);
        var suite = scaffold.files().get(scaffold.suiteFile());

        // when / then
        assertThatCode(() -> SuiteLoader.httpOnly().load(suite, scaffold.suiteFile()))
                .as(spec)
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @MethodSource("specs")
    void suite_validatesAgainstTheSchema(String spec) throws IOException {
        // given
        var scaffold = imported(spec);
        var tree = YAML.readTree(scaffold.files().get(scaffold.suiteFile()));

        // when
        List<String> errors =
                schema.validate(tree).stream().map(Object::toString).toList();

        // then
        assertThat(errors).as(spec).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("specs")
    void environmentFiles_load(String spec) throws IOException {
        // given
        var scaffold = imported(spec);

        // when / then
        assertThat(scaffold.environments()).as(spec).isNotEmpty();
        for (var environment : scaffold.environments()) {
            var text = scaffold.files().get(environment + "/env.yaml");
            assertThat(text).as(environment).isNotNull();
            assertThat(FlatYamlLoader.load(text)).as(environment).containsKey("baseUrl");
        }
    }

    @ParameterizedTest
    @MethodSource("specs")
    void bodyFiles_areJson(String spec) throws IOException {
        // given
        var scaffold = imported(spec);

        // when / then
        for (var file : scaffold.files().entrySet()) {
            if (file.getKey().startsWith("bodies/")) {
                assertThatCode(() -> JSON.readTree(file.getValue()))
                        .as(file.getKey())
                        .doesNotThrowAnyException();
            }
        }
    }

    @ParameterizedTest
    @MethodSource("specs")
    void notes_pointAtFilesTheImportWrites(String spec) throws IOException {
        // when
        var scaffold = imported(spec);

        // then
        assertThat(scaffold.notes())
                .as(spec)
                .allSatisfy(note -> assertThat(scaffold.files()).containsKey(note.file()));
    }
}
