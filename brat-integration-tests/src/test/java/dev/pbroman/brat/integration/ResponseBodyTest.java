package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bodies that are not JSON.
 *
 * <p>Whether a response records a {@code json} namespace at all is decided by what actually came back
 * over the wire, with its real {@code Content-Type} — which is the one thing a hand-written fixture
 * body cannot settle.
 */
class ResponseBodyTest extends EndToEndTestBase {

    @Test
    void run_readsATextBodyAndItsContentType() {
        // when
        var suite = suite("suites/text-body.yaml");
        var result = run(suite, Map.of());

        // then
        assertPassed(result, suite);
        assertThat(result.requestResults().getFirst().status()).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void run_failsTheAssertionThatReadsATextBodyAsJson() {
        // when
        var result = run("suites/text-body-as-json.yaml", Map.of());

        // then — the request completed; the assertion that cannot be interpolated is what failed
        var request = result.requestResults().getFirst();
        assertThat(request.status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(request.responseActionsResult().assertionResults())
                .singleElement()
                .satisfies(assertion -> assertThat(assertion.passed()).isFalse());
    }
}
