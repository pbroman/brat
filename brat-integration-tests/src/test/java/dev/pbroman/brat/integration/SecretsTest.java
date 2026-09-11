package dev.pbroman.brat.integration;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.runner.Environment;
import dev.pbroman.brat.core.secrets.SecretsProviderConfig;
import dev.pbroman.brat.core.secrets.SecretsSource;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A secret making every hop: out of a provider, through interpolation, into a header, onto the wire —
 * and masked on the way back out into what a report would print.
 *
 * <p>No unit test covers the whole path, because the path is the point: each hop is pinned in
 * isolation, and the failure this guards against is a secret arriving correctly and also arriving in
 * the report. The echo endpoint is what makes the first half observable without writing the value into
 * the suite file.
 */
class SecretsTest extends EndToEndTestBase {

    /** What the secrets file holds. The test knows it; the suite file does not. */
    private static final String TOKEN = "s3cr3t-token-value";

    @Test
    void run_resolvesASecretIntoAHeaderAndMasksItInTheOutcome() {
        // when
        var suite = suite("suites/secret-header.yaml");
        var result = BRAT.run(suite, withSecrets());

        // then — the server received the token, which the suite asserted without naming it
        assertPassed(result, suite);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);

        // and — what a report would print is masked
        // The cast is the seam showing: RequestResult carries the RequestDefinition interface, and the
        // outcomes a report reads live on ConfigData, which that interface does not extend.
        var definition = (ConfigData) result.requestResults().getFirst().requestDefinition();
        var outcomes = definition.getOutcomes();
        assertThat(outcomes.get("header.X-Api-Token").reportingString())
                .contains("***")
                .doesNotContain(TOKEN);

        // and — no outcome of this request leaks it anywhere else
        assertThat(outcomes.values())
                .allSatisfy(outcome -> assertThat(outcome.reportingString()).doesNotContain(TOKEN));
    }

    @Test
    void run_failsTheRequestWhenNoProviderHoldsTheSecret() {
        // when — the same suite, run against an environment with no secrets configured
        var result = BRAT.run(suite("suites/secret-header.yaml"), environment(Map.of()));

        // then — a secret that cannot be resolved is this request's failure, not the run's
        assertThat(result.requestResults()).hasSize(1);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Errored.class);
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
