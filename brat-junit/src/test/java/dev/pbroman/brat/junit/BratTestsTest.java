package dev.pbroman.brat.junit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BratTestsTest {

    /**
     * The module's own fixtures, by a path that holds from the module directory (Maven's working
     * directory) and from the repository root alike — which is the search at work.
     */
    private static final String FIXTURES = "brat-junit/src/test/resources/suites/";

    @TempDir
    private Path temp;

    private Path repo;
    private Path service;

    @BeforeEach
    void layOut() throws IOException {
        repo = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectory(repo.resolve(".git"));
        Files.createDirectories(repo.resolve("api-tests/orders-api/local"));
        Files.createDirectories(repo.resolve("envs/ci"));
        service = Files.createDirectories(repo.resolve("service"));
    }

    // --- the launch ---

    @Test
    void launch_findsARelativeSuiteFromTheWorkingDirectoryUp() {
        // when
        var launch = BratTests.suite("api-tests/orders-api").launch(service);

        // then
        assertThat(launch.suite())
                .isEqualTo(repo.resolve("api-tests/orders-api").toString());
    }

    @Test
    void launch_takesASuiteGivenAsAPath() {
        // when
        var launch = BratTests.suite(Path.of("api-tests/orders-api")).launch(service);

        // then
        assertThat(launch.suite())
                .isEqualTo(repo.resolve("api-tests/orders-api").toString());
    }

    @Test
    void launch_carriesAnEnvironmentByName() {
        // when
        var launch =
                BratTests.suite("api-tests/orders-api").environment("local").launch(service);

        // then
        assertThat(launch.environment()).isEqualTo("local");
        assertThat(launch.environmentDirectory()).isNull();
    }

    @Test
    void launch_findsARelativeEnvironmentDirectoryAsTheSuiteIs() {
        // when
        var launch = BratTests.suite("api-tests/orders-api")
                .environmentDirectory("envs/ci")
                .launch(service);

        // then
        assertThat(launch.environmentDirectory())
                .isEqualTo(repo.resolve("envs/ci").toString());
        assertThat(launch.environment()).isNull();
    }

    @Test
    void launch_letsTheLaterOfEnvironmentAndEnvironmentDirectoryWin() {
        // when
        var byName = BratTests.suite("api-tests/orders-api")
                .environmentDirectory("envs/ci")
                .environment("local")
                .launch(service);
        var byDirectory = BratTests.suite("api-tests/orders-api")
                .environment("local")
                .environmentDirectory("envs/ci")
                .launch(service);

        // then
        assertThat(byName.environment()).isEqualTo("local");
        assertThat(byName.environmentDirectory()).isNull();
        assertThat(byDirectory.environment()).isNull();
        assertThat(byDirectory.environmentDirectory())
                .isEqualTo(repo.resolve("envs/ci").toString());
    }

    @Test
    void launch_carriesParamsAndEnvValuesAsEnvParams() {
        // when
        var launch = BratTests.suite("api-tests/orders-api")
                .env("baseUrl", "http://localhost:4711")
                .param("vars.accessToken", "test-token")
                .param("tenant", "acme")
                .launch(service);

        // then
        assertThat(launch.params())
                .containsExactly(
                        Map.entry("env.baseUrl", "http://localhost:4711"),
                        Map.entry("vars.accessToken", "test-token"),
                        Map.entry("tenant", "acme"));
    }

    @Test
    void launch_letsALaterValueForAKeyReplaceTheEarlier() {
        // when
        var launch = BratTests.suite("api-tests/orders-api")
                .env("baseUrl", "http://first")
                .param("env.baseUrl", "http://second")
                .launch(service);

        // then
        assertThat(launch.params()).containsExactly(Map.entry("env.baseUrl", "http://second"));
    }

    @Test
    void everyMethod_leavesTheInstanceItWasCalledOnAsItWas() {
        // given
        var base = BratTests.suite("api-tests/orders-api");

        // when
        base.environment("local");
        base.environmentDirectory("envs/ci");
        base.env("baseUrl", "http://x");
        base.param("tenant", "acme");

        // then
        var launch = base.launch(service);
        assertThat(launch.environment()).isNull();
        assertThat(launch.environmentDirectory()).isNull();
        assertThat(launch.params()).isEmpty();
    }

    // --- refusals while building ---

    @Test
    void suite_refusesABlankOrNullPath() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite(" ")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite((String) null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite((Path) null)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite(Path.of(""))).isInstanceOf(BratException.class);
    }

    @Test
    void environment_refusesABlankName() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite("x").environment(" ")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite("x").environment(null)).isInstanceOf(BratException.class);
    }

    @Test
    void environmentDirectory_refusesABlankPath() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite("x").environmentDirectory("")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite("x").environmentDirectory(null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void env_refusesABlankKeyOrANullValue() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite("x").env(" ", "v")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite("x").env("baseUrl", null)).isInstanceOf(BratException.class);
    }

    @Test
    void param_refusesABlankKeyOrANullValue() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite("x").param(null, "v")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> BratTests.suite("x").param("tenant", null)).isInstanceOf(BratException.class);
    }

    // --- tests(): what fails at once ---

    @Test
    void tests_failsAtOnceForASuiteNotFoundNamingWhereItLooked() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite("no/such/suite").tests())
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'no/such/suite'")
                .hasMessageContaining("(the repository root)");
    }

    @Test
    void tests_failsAtOnceForASuiteTheLoaderRejects() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite(FIXTURES + "broken").tests())
                .isInstanceOf(BratException.class)
                .hasMessageContaining("broken.brat.yaml")
                .hasMessageContaining("requestDefinitio");
    }

    @Test
    void tests_failsAtOnceForAnEnvironmentThatIsNotThere() {
        // when / then
        assertThatThrownBy(() -> BratTests.suite(FIXTURES + "health")
                        .environment("staging")
                        .tests())
                .isInstanceOf(BratException.class)
                .hasMessageContaining("staging");
    }
}
