package dev.pbroman.brat.integration;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.ApacheHttpRequestHandler;
import dev.pbroman.brat.core.launch.Environment;
import dev.pbroman.brat.core.reporting.ConsoleRunReporter;
import dev.pbroman.brat.core.runner.Brat;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsSource;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Auth on real runs: each built-in type reaching a server that checks it, auth inherited and
 * cancelled down a suite tree, a type from a plugin jar, and what an author sees when it goes wrong.
 *
 * <p>The server checks credentials by hand, so a passing assertion here means the header was not only
 * sent but well-formed: a Basic credential encoded wrongly is a 401, not a pass.
 */
class AuthTest extends EndToEndTestBase {

    // ---------- the built-in types ----------

    @Test
    void run_givesEachRoleExactlyTheAccessItShould() {
        // given - the logins capture tokens that the scenarios' bearer auth reads
        var suite = suite("suites/auth/authorization.brat.yaml");

        // when
        var result = BRAT.run(suite, withSecrets());

        // then
        assertPassed(result, suite);
        assertThat(result.suiteErrors()).isEmpty();
    }

    @Test
    void run_sendsBasicAuthTheServerAccepts() {
        // given
        var suite = suite("suites/auth/basic.brat.yaml");

        // when
        var result = BRAT.run(suite, withSecrets());

        // then
        assertPassed(result, suite);
    }

    @Test
    void run_sendsAnApiKeyInEitherHeader() {
        // given
        var suite = suite("suites/auth/apikey.brat.yaml");

        // when
        var result = BRAT.run(suite, withSecrets());

        // then
        assertPassed(result, suite);
    }

    // ---------- inheriting and cancelling ----------

    @Test
    void run_sendsNoCredentialsForNoneThoughTheSuiteDeclaresSome() {
        // given
        var suite = suite("suites/auth/none.brat.yaml");

        // when
        var result = BRAT.run(suite, withSecrets());

        // then - the first request was sent as the suite's account, the second as nobody
        assertPassed(result, suite);
    }

    // ---------- a type from a plugin ----------

    @Test
    void run_appliesAnAuthTypeFromAPluginJar() {
        // given - nothing wires the stub handler; META-INF/services declares it, and the loader accepts
        // its type because this runner registered it
        var suite = suite("suites/auth/plugin-type.brat.yaml");

        // when
        var result = run(suite, Map.of());

        // then
        assertPassed(result, suite);
    }

    // ---------- mistakes, and what an author sees ----------

    @Test
    void load_failsOnAMistypedAuthTypeNamingTheResourceTheLineAndTheKnownTypes() {
        // when
        var thrown = assertThatThrownBy(() -> suite("suites/broken/unknown-auth-type.brat.yaml"));

        // then - before any request is sent
        thrown.isInstanceOf(BratException.class)
                .hasMessageContaining("suites/broken/unknown-auth-type.brat.yaml")
                .hasMessageContaining("beraer")
                .hasMessageContaining("line 5")
                .hasMessageContaining("bearer")
                .hasMessageContaining("stub-signature")
                .hasMessageNotContaining("do-not-print-me-anywhere");
    }

    @Test
    void run_errorsARequestCarryingTheHeaderItsAuthSets() {
        // given - two credentials for one request is ambiguous; none is how to send the hand-written one
        var suite = suiteFrom("""
                name: header conflict
                auth:
                  type: bearer
                  args:
                    token: admin-token
                requests:
                  - name: two credentials
                    requestDefinition:
                      url: "${env.baseUrl}/secure/whoami"
                      headers:
                        Authorization: Bearer hand-written
                  - name: the hand-written one alone
                    auth:
                      type: none
                    requestDefinition:
                      url: "${env.baseUrl}/secure/whoami"
                      headers:
                        Authorization: Bearer reader-token
                    responseActions:
                      assertions:
                        - a: "${response.json.$.username}"
                          func: isEqualTo
                          b: reader
                """);

        // when
        var result = run(suite, Map.of());

        // then - the first is Errored, pointing at the fix and quoting neither credential
        var conflicting = result.requestResults().get(0);
        assertThat(conflicting.status())
                .isInstanceOfSatisfying(
                        RequestStatus.Errored.class,
                        errored -> assertThat(errored.message())
                                .contains("Authorization")
                                .contains("type: none")
                                .doesNotContain("hand-written")
                                .doesNotContain("admin-token"));

        // and - the fix it points at works
        var handWritten = result.requestResults().get(1);
        assertThat(handWritten.failed()).isFalse();
        assertThat(handWritten.responseActionsResult().assertionResults())
                .singleElement()
                .satisfies(assertion -> assertThat(assertion.passed()).isTrue());
    }

    @Test
    void run_abortsTheSuiteOfALoginThatInheritsTheAuthItFeeds() {
        // given - the login sits inside the suite whose auth reads the token it is about to fetch
        var suite = suiteFrom("""
                name: login in the wrong place
                subSuites:
                  - name: admin
                    auth:
                      type: bearer
                      args:
                        token: "${vars.adminToken}"
                    requests:
                      - name: log in as admin
                        phase: setup
                        requestDefinition:
                          url: "${env.baseUrl}/login"
                          method: POST
                          headers:
                            Content-Type: application/json
                          body:
                            raw: |
                              {
                                "username": "admin",
                                "password": "${secrets.adminPassword}"
                              }
                        responseActions:
                          setVars:
                            adminToken: "${response.json.$.token}"
                      - name: deletes a user
                        requestDefinition:
                          url: "${env.baseUrl}/secure/users/42"
                          method: DELETE
                """);

        // when
        var result = BRAT.run(suite, withSecrets());

        // then - the login is Errored on its blank token, rather than sent without one
        assertThat(result.requestResults()).singleElement().satisfies(login -> {
            assertThat(login.coordinates().name()).isEqualTo("log in as admin");
            assertThat(login.status())
                    .isInstanceOfSatisfying(
                            RequestStatus.Errored.class,
                            errored -> assertThat(errored.message()).contains("token"));
        });

        // and - its suite is aborted, so nothing runs as an admin nobody logged in as
        assertThat(result.suiteErrors())
                .singleElement()
                .satisfies(error -> assertThat(error.path()).isEqualTo("login in the wrong place/admin"));
    }

    @Test
    void run_printsTheAppliedAuthMaskedButForItsScheme() {
        // given - a console over a buffer, and a request that fails so the console prints it as sent
        var buffer = new ByteArrayOutputStream();
        var suite = suiteFrom("""
                name: masked auth
                requests:
                  - name: who am I
                    auth:
                      type: bearer
                      args:
                        token: reader-token
                    requestDefinition:
                      url: "${env.baseUrl}/secure/whoami"
                    responseActions:
                      assertions:
                        - a: "${response.json.$.username}"
                          func: isEqualTo
                          b: admin
                          message: fails on purpose, so the request is printed
                """);
        try (var handler = new ApacheHttpRequestHandler()) {
            var brat = Brat.builder()
                    .requestHandler(handler)
                    .runReporter(new ConsoleRunReporter(new PrintStream(buffer, true, StandardCharsets.UTF_8)))
                    .build();

            // when
            brat.run(suite, environment(Map.of()));
        }

        // then - auth was applied, and by which type, without the credential
        var printed = buffer.toString(StandardCharsets.UTF_8);
        assertThat(printed)
                .contains("header.Authorization: auth bearer → Bearer ***")
                .doesNotContain("reader-token");
    }

    /**
     * An environment whose chain is the file provider reading this module's secrets resource, which
     * holds the test server's passwords and API key.
     *
     * @return the environment
     */
    private static Environment withSecrets() {
        var source = new SecretsSource("file", Map.of("location", "classpath:secrets/integration-secrets.yaml"));
        return new Environment(
                Map.of("baseUrl", baseUrl()), Map.of(), new SecretsProviderConfig(Map.of(), List.of(source)));
    }
}
