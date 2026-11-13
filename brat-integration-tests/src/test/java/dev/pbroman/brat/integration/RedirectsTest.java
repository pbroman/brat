package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redirects against a real server: not followed by default, followed when the run says so, and
 * refused across origins while the request carries credentials.
 */
class RedirectsTest extends EndToEndTestBase {

    /** The same server under another host name, which makes it another origin. */
    private static String otherBaseUrl() {
        return baseUrl().replace("localhost", "127.0.0.1");
    }

    @Test
    void run_returnsTheRedirectItselfByDefault() {
        // when
        var suite = suite("suites/redirects/not-followed.brat.yaml");
        var result = run(suite, Map.of());

        // then
        assertPassed(result, suite);
    }

    @Test
    void run_followsWhenTheEnvironmentTurnsItOnAndARequestCanPinItOff() {
        // when
        var suite = suite("suites/redirects/followed.brat.yaml");
        var result = run(suite, Map.of("followRedirects", "true"));

        // then
        assertPassed(result, suite);
    }

    @Test
    void run_refusesAnotherOriginWithCredentialsAndRecordsWhere() {
        // when
        var suite = suite("suites/redirects/credentials-elsewhere.brat.yaml");
        var result = run(suite, Map.of("otherBaseUrl", otherBaseUrl()));

        // then - the 3xx came back, and the response says which redirect was refused
        assertPassed(result, suite);
        assertThat(result.requestResults().getFirst().responseVars())
                .hasValueSatisfying(vars ->
                        assertThat(vars).containsEntry("redirectNotFollowed", otherBaseUrl() + "/secure/whoami"));
    }
}
