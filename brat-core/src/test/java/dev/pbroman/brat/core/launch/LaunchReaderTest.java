package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.loader.SuiteLoader;
import dev.pbroman.brat.core.secrets.FileSecretsProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class LaunchReaderTest {

    private static final String SUITE = """
            name: orders
            requests:
              - name: list orders
                requestDefinition:
                  url: "${env.baseUrl}/orders"
            """;

    @TempDir
    Path dir;

    private final LaunchReader underTest =
            new LaunchReader(SuiteLoader.httpOnly(), new EnvironmentReader(List.of(new FileSecretsProviderFactory())));

    // ---------- the suite ----------

    @Test
    void read_loadsTheSuiteAtAnAbsolutePath() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);

        // when
        var result = underTest.read(Launch.of(suite.toString()));

        // then
        assertThat(result.suite().name()).isEqualTo("orders");
        assertThat(result.suite().requests()).hasSize(1);
    }

    @Test
    void read_acceptsAFilePrefixedSuite() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);

        // when
        var result = underTest.read(Launch.of("file:" + suite));

        // then
        assertThat(result.suite().name()).isEqualTo("orders");
    }

    @Test
    void read_loadsASuiteFromTheClasspath() {
        // when
        var result = underTest.read(Launch.of("classpath:launch/hello.brat.yaml"));

        // then
        assertThat(result.suite().name()).isEqualTo("hello");
    }

    @Test
    void read_namesASuiteThatCannotBeRead() {
        // given
        var missing = dir.resolve("missing.brat.yaml").toString();

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(missing)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("missing.brat.yaml");
    }

    @Test
    void read_namesTheResolvedLocationAsTheOriginOfALoadError() throws IOException {
        // given
        var suite = write("broken.brat.yaml", "name: s\nrequestz: []\n");

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(suite.toString())))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("file:" + suite)
                .hasMessageContaining("requestz");
    }

    // ---------- the join ----------

    @Test
    void read_givesTheEnvironmentTheSuitesResolvedFileLocation() throws IOException {
        // given - written with a redundant segment, which the resolved location no longer has
        var suite = write("orders.brat.yaml", SUITE);

        // when
        var result =
                underTest.read(Launch.of(dir.resolve("x/../orders.brat.yaml").toString()));

        // then - the prefix is part of it: a bare body path resolves in the same medium
        assertThat(result.environment().suiteLocation()).isEqualTo("file:" + suite);
    }

    @Test
    void read_givesTheEnvironmentAClasspathSuitesLocation() {
        // when
        var result = underTest.read(Launch.of("classpath:launch/hello.brat.yaml"));

        // then
        assertThat(result.environment().suiteLocation()).isEqualTo("classpath:launch/hello.brat.yaml");
    }

    @Test
    void read_joinsTheSuiteLocationToAnEnvironmentReadFromADirectory() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);
        var dev = Files.createDirectory(dir.resolve("dev"));
        Files.writeString(dev.resolve("env.yaml"), "baseUrl: http://localhost:8080\n");

        // when
        var result = underTest.read(Launch.of(suite.toString()).withEnvironmentDirectory(dev.toString()));

        // then
        assertThat(result.environment().env()).containsEntry("baseUrl", "http://localhost:8080");
        assertThat(result.environment().suiteLocation()).isEqualTo("file:" + suite);
    }

    // ---------- the environment ----------

    @Test
    void read_givesALaunchWithoutADirectoryAnEmptyEnvAndNoSecretsSources() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);

        // when
        var result = underTest.read(Launch.of(suite.toString()));

        // then - environment variables remain, since the chain always ends with them
        assertThat(result.environment().env()).isEmpty();
        assertThat(result.environment().secretsConfig().sources()).isEmpty();
        assertThat(result.environment().secretsConfig().providerParams()).isEmpty();
    }

    @Test
    void read_carriesTheParamsWithOrWithoutADirectory() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);
        var dev = Files.createDirectory(dir.resolve("dev"));
        var params = Map.of("timeout", "20000");

        // when
        var without = underTest.read(Launch.of(suite.toString()).withParams(params));
        var with = underTest.read(Launch.of(suite.toString())
                .withEnvironmentDirectory(dev.toString())
                .withParams(params));

        // then
        assertThat(without.environment().params()).containsOnly(entry("timeout", "20000"));
        assertThat(with.environment().params()).containsOnly(entry("timeout", "20000"));
    }

    @Test
    void read_rejectsATokenInAParam() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(suite.toString()).withParams(Map.of("wait", "${x}"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("params.wait");
    }

    @Test
    void read_rejectsAnEnvironmentDirectoryThatIsNotThere() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);
        var missing = dir.resolve("prod").toString();

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(suite.toString()).withEnvironmentDirectory(missing)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("prod");
    }

    @Test
    void read_reportsTheSuiteWhenBothTheSuiteAndTheDirectoryAreWrong() {
        // given
        var launch = Launch.of(dir.resolve("missing.brat.yaml").toString())
                .withEnvironmentDirectory(dir.resolve("prod").toString());

        // then
        assertThatThrownBy(() -> underTest.read(launch))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("missing.brat.yaml")
                .hasMessageNotContaining("prod");
    }

    // ---------- arguments ----------

    @Test
    void read_throwsForANullLaunch() {
        // then
        assertThatThrownBy(() -> underTest.read(null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsForANullLoaderOrEnvironmentReader() {
        // given
        var environmentReader = new EnvironmentReader(List.of());

        // then
        assertThatThrownBy(() -> new LaunchReader(null, environmentReader)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new LaunchReader(SuiteLoader.httpOnly(), null)).isInstanceOf(BratException.class);
    }

    /**
     * Writes a file into the test directory.
     *
     * @param name the file name
     * @param content its content
     * @return the written file
     * @throws IOException if it cannot be written
     */
    private Path write(String name, String content) throws IOException {
        return Files.writeString(dir.resolve(name), content);
    }
}
