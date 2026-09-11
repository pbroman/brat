package dev.pbroman.brat.integration;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What becomes an {@code Errored} request and what does not.
 *
 * <p>The line BRAT draws: a status code is something the server said, so every one of them is a
 * response with a body to assert on; a transport failure and an elapsed timeout are not. Both sides
 * need a real socket — a stub handler can only return what a test told it to.
 */
class RequestErrorsTest extends EndToEndTestBase {

    @ParameterizedTest
    @ValueSource(ints = {200, 404, 500, 503})
    void run_treatsEveryStatusCodeAsAResponse(int code) {
        // when
        var suite = suite("suites/status-code.yaml");
        var result = run(suite, Map.of("code", String.valueOf(code)));

        // then — their 504 is a response; assertions run and the body survives
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);
        assertPassed(result, suite);
    }

    @Test
    void run_errorsAndCarriesOnWhenAHostIsUnreachable() {
        // when — the first request goes to a port nothing listens on, the second to the real server
        var result = run("suites/two-hosts.yaml", Map.of("deadUrl", unusedUrl()));

        // then — a transport failure is this request's data, not the run's end
        assertThat(result.requestResults()).hasSize(2);
        var unreachable = result.requestResults().getFirst();
        assertThat(unreachable.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(((RequestStatus.Errored) unreachable.status()).message()).contains("/status/200");
        assertThat(result.requestResults().get(1).status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(result.failed()).isTrue();

        // and — nothing ran assertions over a response that never arrived
        assertThat(result.requestResults().getFirst().responseActionsResult().assertionResults())
                .isEmpty();
    }

    @Test
    void run_errorsWhenTheRequestOutlivesItsOwnTimeout() {
        // when — the server answers after 800ms, the request allows 200
        var result = run("suites/slow.yaml", Map.of("delayMs", "800", "timeout", "200"));

        // then — our timeout is not a response
        var request = result.requestResults().getFirst();
        assertThat(request.status()).isInstanceOf(RequestStatus.Errored.class);

        // and — it was the timeout that bit, not some other transport failure: a refusal returns in
        // about a millisecond, and waiting for the server would have taken the full 800ms
        assertThat(request.elapsedMs()).isGreaterThanOrEqualTo(200L).isLessThan(800L);
    }

    @Test
    void run_completesWhenTheAnswerArrivesInsideTheTimeout() {
        // when — the same suite, given the time
        var suite = suite("suites/slow.yaml");
        var result = run(suite, Map.of("delayMs", "50", "timeout", "5000"));

        // then — so the test above measures the timeout and not merely "slow errors"
        assertPassed(result, suite);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);
    }

    /**
     * A URL on a port nothing is listening on: bound to learn a free port, then released.
     *
     * @return a URL that refuses a connection
     */
    private static String unusedUrl() {
        try (var socket = new ServerSocket(0)) {
            return "http://localhost:" + socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find a free port to not listen on", e);
        }
    }
}
