package dev.pbroman.brat.integration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.launch.Launch;
import dev.pbroman.brat.core.launch.LaunchReader;
import dev.pbroman.brat.core.reporting.ConsoleRunReporter;
import dev.pbroman.brat.core.runner.Brat;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A run launched the way a person launches one: a suite file and an environment directory, both on
 * disk, and launch params for what changes per run.
 *
 * <p>The fixture under {@code projects/users} is laid out as the suite-author documentation lays a
 * project out. Everything reaches the run through files: the bare body path resolves next to the
 * suite, {@code env.yaml} fills the body, {@code secrets.yaml} supplies the token — and the committed
 * {@code secrets.example.yaml}, which would win if it were read, is not.
 */
class LaunchTest extends EndToEndTestBase {

    /** What {@code dev/secrets.yaml} holds. */
    private static final String DIRECTORY_TOKEN = "dev-token-from-the-directory";

    /** What {@code dev/secrets.example.yaml} holds, and what must never be used. */
    private static final String EXAMPLE_TOKEN = "example-value-never-used";

    @Test
    void run_launchesASuiteFileAgainstItsEnvironmentDirectory() {
        // given - env.yaml points baseUrl nowhere, so only the override reaches the server
        var launch = launch(Map.of("env.baseUrl", baseUrl()));

        // when
        var result = BRAT.run(launch);

        // then - created from the bare body file, filled from env.yaml
        assertPassed(
                result,
                new LaunchReader(BRAT.loader(), BRAT.environmentReader())
                        .read(launch)
                        .suite());
        // and - the server holds exactly that user: the search endpoint answers with a list
        assertThat(crud.get("/search/launched-from-files").size()).isEqualTo(1);
    }

    @Test
    void run_takesTheTokenFromSecretsYamlAndIgnoresTheExampleFile() {
        // when
        var result = BRAT.run(launch(Map.of("env.baseUrl", baseUrl())));

        // then
        assertThat(echoed(result)).contains(DIRECTORY_TOKEN).doesNotContain(EXAMPLE_TOKEN);
    }

    @Test
    void run_letsALaunchParamOverrideASecretAndMasksIt() {
        // given
        var override = "overridden-at-launch";

        // when
        var result = BRAT.run(launch(Map.of("env.baseUrl", baseUrl(), "secrets.apiToken", override)));

        // then - the override reached the wire ahead of secrets.yaml, and no report can print it
        assertThat(echoed(result)).contains(override).doesNotContain(DIRECTORY_TOKEN);
        var sent = (ConfigData) result.requestResults().getLast().requestDefinition();
        assertThat(sent.getOutcomes().values())
                .allSatisfy(outcome -> assertThat(outcome.reportingString()).doesNotContain(override));
    }

    @Test
    void run_reportsALaunchedRunOnTheConsoleWithoutItsToken() {
        // given - a runner given no handler at all, which supplies its own and closes it
        var buffer = new ByteArrayOutputStream();
        try (var brat = Brat.builder()
                .runReporter(new ConsoleRunReporter(new PrintStream(buffer, true, StandardCharsets.UTF_8)))
                .build()) {

            // when
            brat.run(launch(Map.of("env.baseUrl", baseUrl())));
        }

        // then
        assertThat(buffer.toString(StandardCharsets.UTF_8))
                .contains("create a user")
                .contains("echo the token")
                .doesNotContain(DIRECTORY_TOKEN);
    }

    /**
     * A launch of the fixture suite against its {@code dev} directory, both as filesystem paths.
     *
     * @param params the launch params
     * @return the launch
     */
    private static Launch launch(Map<String, String> params) {
        var project = project();
        return Launch.of(project.resolve("users.brat.yaml").toString())
                .withEnvironmentDirectory(project.resolve("dev").toString())
                .withParams(params);
    }

    /**
     * The fixture project's directory on disk.
     *
     * @return its path
     */
    private static Path project() {
        try {
            return Path.of(LaunchTest.class
                    .getClassLoader()
                    .getResource("projects/users")
                    .toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The fixture project is not where the test expects it", e);
        }
    }

    /**
     * The body the echo endpoint answered with: the headers that reached the server.
     *
     * @param result the run
     * @return the echoed body
     */
    private static String echoed(RunResult result) {
        var status = (RequestStatus.Completed) result.requestResults().getLast().status();
        return String.valueOf(status.responseVars().get(BODY));
    }
}
