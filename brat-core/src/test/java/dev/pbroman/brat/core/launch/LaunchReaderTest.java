package dev.pbroman.brat.core.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.loader.SuiteLoader;
import dev.pbroman.brat.core.secrets.FileSecretsProviderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
        assertThat(result.environment().secretsConfig().providerArgs()).isEmpty();
    }

    @Test
    void read_carriesTheParamsWithOrWithoutADirectory() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);
        write("dev/env.yaml", "baseUrl: http://dev.example\n");
        var dev = dir.resolve("dev");
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

    // ---------- a directory as the suite ----------

    @Test
    void read_loadsTheOneSuiteInADirectory() throws IOException {
        // given - a project: its suite, an environment and bodies beside it, and a README
        write("orders.brat.yaml", SUITE);
        write("README.md", "# orders\n");
        write("dev/env.yaml", "baseUrl: http://localhost:8080\n");
        write("bodies/new-order.json", "{}\n");

        // when
        var result = underTest.read(Launch.of(dir.toString()));

        // then - the suite file, not the directory, is what bare paths in the suite resolve against
        assertThat(result.suite().name()).isEqualTo("orders");
        assertThat(result.environment().suiteLocation()).endsWith("orders.brat.yaml");
    }

    @Test
    void read_loadsABratYmlSuiteFromADirectory() throws IOException {
        // given
        write("orders.brat.yml", SUITE);

        // when
        var result = underTest.read(Launch.of(dir.toString()));

        // then
        assertThat(result.suite().name()).isEqualTo("orders");
    }

    @Test
    void read_rejectsADirectoryHoldingNoSuiteNamingIt() throws IOException {
        // given - a suite one level down does not count, and neither does a plain YAML file
        write("nested/orders.brat.yaml", SUITE);
        write("orders.yaml", SUITE);

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(dir.toString())))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(dir.toString())
                .hasMessageContaining(".brat.yaml");
    }

    @Test
    void read_rejectsADirectoryHoldingSeveralSuitesNamingEachSorted() throws IOException {
        // given
        write("smoke.brat.yaml", SUITE);
        write("orders.brat.yaml", SUITE);

        // then - running several is not a launch; the message says how to pick one
        assertThatThrownBy(() -> underTest.read(Launch.of(dir.toString())))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(dir.toString())
                .hasMessageContainingAll("orders.brat.yaml", "smoke.brat.yaml")
                .hasMessageMatching("(?s).*orders\\.brat\\.yaml.*smoke\\.brat\\.yaml.*")
                .hasMessageContaining("name one");
    }

    // ---------- an environment by name ----------

    @Test
    void read_readsTheEnvironmentNamedBesideASuiteFile() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);
        write("dev/env.yaml", "baseUrl: http://dev.example\n");

        // when
        var result = underTest.read(Launch.of(suite.toString()).withEnvironment("dev"));

        // then
        assertThat(result.environment().env()).containsEntry("baseUrl", "http://dev.example");
    }

    @Test
    void read_readsTheEnvironmentNamedBesideASuiteFoundInADirectory() throws IOException {
        // given
        write("orders.brat.yaml", SUITE);
        write("ci/env.yaml", "baseUrl: http://ci.example\n");

        // when
        var result = underTest.read(Launch.of(dir.toString()).withEnvironment("ci"));

        // then
        assertThat(result.environment().env()).containsEntry("baseUrl", "http://ci.example");
    }

    @Test
    void read_carriesTheParamsWithANamedEnvironment() throws IOException {
        // given
        write("orders.brat.yaml", SUITE);
        write("dev/env.yaml", "baseUrl: http://dev.example\n");

        // when
        var result =
                underTest.read(Launch.of(dir.toString()).withEnvironment("dev").withParams(Map.of("timeout", "20000")));

        // then
        assertThat(result.environment().params()).containsOnly(entry("timeout", "20000"));
    }

    @Test
    void read_rejectsAMissingEnvironmentListingTheAvailableOnesSorted() throws IOException {
        // given - bodies/ holds no environment file, so it is not offered
        write("orders.brat.yaml", SUITE);
        write("dev/env.yaml", "baseUrl: http://dev.example\n");
        write("ci/secrets.yaml", "apiKey: k\n");
        write("bodies/new-order.json", "{}\n");

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(dir.toString()).withEnvironment("prod")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'prod'")
                .hasMessageContaining(dir.toString())
                .hasMessageContaining("ci, dev")
                .hasMessageNotContaining("bodies");
    }

    @Test
    void read_saysSoWhenNoEnvironmentIsAvailable() throws IOException {
        // given
        write("orders.brat.yaml", SUITE);

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(dir.toString()).withEnvironment("dev")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'dev'")
                .hasMessageContaining("none");
    }

    @Test
    void read_rejectsANamedDirectoryHoldingNoEnvironmentFile() throws IOException {
        // given - bodies/ exists, but is not an environment
        write("orders.brat.yaml", SUITE);
        write("bodies/new-order.json", "{}\n");

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(dir.toString()).withEnvironment("bodies")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("bodies")
                .hasMessageContaining("env.yaml");
    }

    @Test
    void read_leavesADirectoryThatCannotBeListedOutOfTheAvailableEnvironments() throws IOException {
        // given - meaningless on a filesystem without POSIX permissions, or as root
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        write("orders.brat.yaml", SUITE);
        write("dev/env.yaml", "baseUrl: http://dev.example\n");
        write("locked/env.yaml", "baseUrl: http://locked.example\n");
        var locked = dir.resolve("locked");
        Files.setPosixFilePermissions(locked, Set.of());
        assumeFalse(Files.isReadable(locked), "running with permission to read any directory");

        try {
            // then
            assertThatThrownBy(() -> underTest.read(Launch.of(dir.toString()).withEnvironment("prod")))
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("available: dev")
                    .hasMessageNotContaining("locked");
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void read_namesTheSuiteDirectoryWhenItCannotBeListed() throws IOException {
        // given - the suite file can be read by its path, but its directory cannot be listed
        assumeTrue(dir.getFileSystem().supportedFileAttributeViews().contains("posix"));
        var project = Files.createDirectory(dir.resolve("project"));
        var suite = Files.writeString(project.resolve("orders.brat.yaml"), SUITE);
        Files.setPosixFilePermissions(project, PosixFilePermissions.fromString("-wx------"));
        assumeFalse(Files.isReadable(project), "running with permission to read any directory");

        try {
            // then
            assertThatThrownBy(() -> underTest.read(Launch.of(suite.toString()).withEnvironment("prod")))
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining(project.toString())
                    .hasMessageContaining("cannot be listed");
        } finally {
            Files.setPosixFilePermissions(project, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @Test
    void read_rejectsAnEnvironmentNameBesideAClasspathSuite() {
        // then - a classpath directory cannot be listed
        assertThatThrownBy(() -> underTest.read(
                        Launch.of("classpath:launch/hello.brat.yaml").withEnvironment("dev")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("classpath");
    }

    @Test
    void read_rejectsAnEnvironmentDirectoryHoldingNoEnvironmentFile() throws IOException {
        // given
        var suite = write("orders.brat.yaml", SUITE);
        write("typo/README.md", "# nothing here\n");

        // then
        assertThatThrownBy(() -> underTest.read(Launch.of(suite.toString())
                        .withEnvironmentDirectory(dir.resolve("typo").toString())))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("typo");
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
     * Writes a file into the test directory, creating the directories its name holds.
     *
     * @param name the file name, possibly with directories before it
     * @param content its content
     * @return the written file
     * @throws IOException if it cannot be written
     */
    private Path write(String name, String content) throws IOException {
        var file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }
}
