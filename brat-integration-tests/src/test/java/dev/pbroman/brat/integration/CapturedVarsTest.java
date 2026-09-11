package dev.pbroman.brat.integration;

import java.util.Map;

import dev.pbroman.brat.core.data.result.CaptureFailure;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What {@code setVars} captures from one response, and what a later request does with it.
 *
 * <p>This is the one aspect whose fixture is built out of BRAT itself: "a capture feeds the next
 * request" is the claim, so the request that produces the value has to be BRAT's. It is also the only
 * place a tombstone can be observed — a failed capture and the request that reads it are two different
 * requests, which no unit test assembles.
 */
class CapturedVarsTest extends EndToEndTestBase {

    @Test
    void run_feedsACapturedVarIntoTheNextRequest() {
        // when
        var suite = suite("suites/capture-and-reuse.yaml");
        var result = run(suite, Map.of("username", "captured"));

        // then
        assertPassed(result, suite);
        assertThat(result.requestResults()).hasSize(2);
        assertThat(result.requestResults())
                .allSatisfy(request -> assertThat(request.status()).isInstanceOf(RequestStatus.Completed.class));

        // and — the second request really read the user the first one created
        assertThat(crud.get("/all").size()).isEqualTo(1);
    }

    @Test
    void run_recordsACaptureFailureWithoutEndingTheRun() {
        // when — 'nickname' names a path the body does not hold
        var result = run("suites/capture-fails.yaml", Map.of("username", "tombstoned"));

        // then — the request completed, and the failure is data on it
        var create = result.requestResults().getFirst();
        assertThat(create.status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(create.responseActionsResult().captureFailures())
                .extracting(CaptureFailure::name)
                .containsExactly("nickname");
        assertThat(create.failed()).isTrue();

        // and — the failure is the capture's, not the assertion's: that one ran and passed
        assertThat(create.responseActionsResult().assertionResults())
                .singleElement()
                .satisfies(assertion -> assertThat(assertion.passed()).isTrue());
    }

    @Test
    void run_evaluatesTheCapturesWrittenAfterAFailedOne() {
        // when — 'nickname' fails first, and 'userId' is authored after it
        var result = run("suites/capture-fails.yaml", Map.of("username", "tombstoned"));

        // then — the third request could only be built if userId was captured despite the failure
        var reader = result.requestResults().getLast();
        assertThat(reader.status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(reader.responseActionsResult().assertionResults())
                .singleElement()
                .satisfies(assertion -> assertThat(assertion.passed()).isTrue());
    }

    @Test
    void run_failsALaterReadOfAFailedCaptureNamingTheCause() {
        // when
        var result = run("suites/capture-fails.yaml", Map.of("username", "tombstoned"));

        // then — the reader errors, and says which var and which request, rather than 404ing later
        assertThat(result.requestResults()).hasSize(3);
        var reader = result.requestResults().get(1);
        assertThat(reader.status()).isInstanceOf(RequestStatus.Errored.class);
        var errored = (RequestStatus.Errored) reader.status();
        assertThat(errored.message()).contains("nickname").contains("capture fails/create a user");
    }
}
