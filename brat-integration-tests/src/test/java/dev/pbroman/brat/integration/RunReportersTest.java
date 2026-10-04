package dev.pbroman.brat.integration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.ApacheHttpRequestHandler;
import dev.pbroman.brat.core.launch.Environment;
import dev.pbroman.brat.core.reporting.ConsoleRunReporter;
import dev.pbroman.brat.core.runner.Brat;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsSource;
import dev.pbroman.brat.integration.stub.StubRunReporter;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import dev.pbroman.brat.integration.support.TestRunControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Run reporters on real runs: one from a plugin jar, and the console core ships. */
class RunReportersTest extends EndToEndTestBase {

    /** What the secrets file holds. The test knows it; the suite file does not. */
    private static final String TOKEN = "s3cr3t-token-value";

    @Test
    void reporter_deliversARealRunToAReporterFromAPluginJar() {
        // given - nothing wires the stub reporter; META-INF/services declares it
        var listener = (StubRunReporter.Recording) BRAT.reporter(StubRunReporter.NAME, Map.of("label", "it"));

        // when
        var result = BRAT.run(
                suite("suites/two-creates.brat.yaml"), environment(Map.of()), List.of(listener), new TestRunControl());

        // then - its arguments arrived, and so did the whole run
        assertThat(listener.label()).isEqualTo("it");
        assertThat(listener.events().getFirst()).isInstanceOf(RunEvent.RunStarted.class);
        assertThat(listener.events().getLast()).isEqualTo(new RunEvent.RunFinished(result));
        assertThat(listener.events())
                .filteredOn(RunEvent.RequestFinished.class::isInstance)
                .hasSize(2);
    }

    @Test
    void reporter_rejectsAnArgumentThePluginDoesNotKnowBeforeTheRun() {
        // when / then
        assertThatThrownBy(() -> BRAT.reporter(StubRunReporter.NAME, Map.of("lable", "it")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("lable");
    }

    @Test
    void run_printsAFailingRequestToTheConsoleWithItsSecretMasked() {
        // given - a console over a buffer replaces core's, and is reached as the default reporter
        var buffer = new ByteArrayOutputStream();
        try (var handler = new ApacheHttpRequestHandler()) {
            var brat = Brat.builder()
                    .requestHandler(handler)
                    .runReporter(new ConsoleRunReporter(new PrintStream(buffer, true, StandardCharsets.UTF_8)))
                    .build();

            // when
            brat.run(suite("suites/failing-secret-header.brat.yaml"), withSecrets());
        }

        // then - the request as sent is printed, because it failed, and the token is not
        var printed = buffer.toString(StandardCharsets.UTF_8);
        assertThat(printed)
                .contains("a failing secret header")
                .contains("send the token")
                .contains("the status must be Created")
                .contains("X-Api-Token")
                .contains("***")
                .contains("FAILED")
                .doesNotContain(TOKEN);
    }

    /**
     * An environment whose chain is the file provider reading this module's secrets resource.
     *
     * @return the environment
     */
    private static Environment withSecrets() {
        var source = new SecretsSource("file", Map.of("location", "classpath:secrets/integration-secrets.yaml"));
        return new Environment(
                Map.of("baseUrl", baseUrl()), Map.of(), new SecretsProviderConfig(Map.of(), List.of(source)));
    }
}
