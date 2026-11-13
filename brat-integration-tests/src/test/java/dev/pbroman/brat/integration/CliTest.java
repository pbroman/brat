package dev.pbroman.brat.integration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;

import dev.pbroman.brat.cli.BratCli;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The {@code brat} command, run the way a person runs it: a project directory, an environment named
 * beside it, and a launch param for what changes per run — through the command's entry point, against
 * the server.
 *
 * <p>The fixture is the one {@link LaunchTest} launches from Java, so the two tell the same story from
 * the two entry points. Its {@code env.yaml} points {@code baseUrl} nowhere, so the run reaching the
 * server is itself the proof that {@code -p env.baseUrl=…} arrived.
 */
class CliTest extends EndToEndTestBase {

    /** What {@code dev/secrets.yaml} holds, and what no output may show. */
    private static final String TOKEN = "dev-token-from-the-directory";

    /** The token the server's {@code /oauth/token} issues to {@code reader}. */
    private static final String ISSUED_TOKEN = "reader-token";

    @Test
    void run_runsAProjectDirectoryWithANamedEnvironmentAndExitsWith0() throws URISyntaxException {
        // given
        var project = Path.of(getClass()
                        .getClassLoader()
                        .getResource("projects/users")
                        .toURI())
                .toString();

        // when
        var result = brat("run", project, "-e", "dev", "-p", "env.baseUrl=" + baseUrl());

        // then - it passed, and says which suite and which environment it tested
        assertThat(result.exitCode()).isZero();
        var lines = result.out().lines().toList();
        assertThat(lines.getFirst())
                .isEqualTo("brat run " + Path.of(project).resolve("users.brat.yaml") + " · environment dev");
        assertThat(lines.getLast()).endsWith("PASSED");

        // and - the user it created is on the server, which only the server can say
        assertThat(crud.get("/search/launched-from-files").size()).isEqualTo(1);

        // and - the token reached the wire without reaching either stream
        assertThat(result.out()).doesNotContain(TOKEN);
        assertThat(result.err()).doesNotContain(TOKEN);
    }

    @Test
    void run_showsTheResponseOfAFailedRequestWithItsTokenMasked() throws URISyntaxException {
        // when
        var result = brat("run", project("tokens"), "-p", "env.baseUrl=" + baseUrl());

        // then - the check failed, and the response says why: the token type is Bearer
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.out())
                .contains("response: 200")
                .contains("\"token_type\" : \"Bearer\"")
                .contains("\"access_token\" : \"***\"")
                .doesNotContain(ISSUED_TOKEN);
    }

    @Test
    void run_showsATokenFromTheResponseWithShowSecrets() throws URISyntaxException {
        // when
        var result = brat("run", project("tokens"), "-p", "env.baseUrl=" + baseUrl(), "--show-secrets");

        // then
        assertThat(result.out()).contains("\"access_token\" : \"" + ISSUED_TOKEN + "\"");
    }

    @Test
    void run_masksASecretTheServerEchoesEvenWithShowSecrets() throws URISyntaxException {
        // when - the users suite sends a secret to an endpoint that echoes it back
        var result = brat(
                "run",
                project("users"),
                "-e",
                "dev",
                "-p",
                "env.baseUrl=" + baseUrl(),
                "--show-response",
                "all",
                "--show-request",
                "all",
                "--show-secrets");

        // then - every response is shown, and the echoed secret in none of them
        assertThat(result.exitCode()).isZero();
        assertThat(result.out()).contains("response: 200").contains("request:").doesNotContain(TOKEN);
    }

    @Test
    void run_exitsWith2ForAScopeTheReporterRejectsBeforePrintingAnything() throws URISyntaxException {
        // when
        var result = brat("run", project("tokens"), "-p", "env.baseUrl=" + baseUrl(), "--show-assertions", "none");

        // then
        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.out()).isEmpty();
        assertThat(result.err()).contains("none");
    }

    private String project(String name) throws URISyntaxException {
        return Path.of(getClass()
                        .getClassLoader()
                        .getResource("projects/" + name)
                        .toURI())
                .toString();
    }

    /** Runs the command in-process with stdout and stderr captured, as {@code BratCli.main} would. */
    private static Result brat(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var originalOut = System.out;
        var originalErr = System.err;
        System.setOut(new PrintStream(out, true, UTF_8));
        System.setErr(new PrintStream(err, true, UTF_8));
        try {
            var commandLine = new CommandLine(new BratCli());
            commandLine.setOut(new PrintWriter(System.out, true));
            commandLine.setErr(new PrintWriter(System.err, true));
            var exitCode = commandLine.execute(args);
            return new Result(exitCode, out.toString(UTF_8), err.toString(UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Result(int exitCode, String out, String err) {}
}
