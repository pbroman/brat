package dev.pbroman.brat.integration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.integration.support.EndToEndTestBase;
import dev.pbroman.brat.integration.support.TestRunControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code repeatUntil} against a server that changes its answer.
 *
 * <p>A poll condition reads the response the loop is waiting for, so it is the one construct that
 * cannot be exercised without a server that answers differently over time: a stub returning a fixed
 * body can prove the loop counts, never that the condition is evaluated against each fresh response.
 * The job endpoint reports {@code pending} until it has been polled often enough.
 */
class PollingTest extends EndToEndTestBase {

    @Test
    void run_pollsUntilTheConditionHoldsAndEmitsOneEventPerAttempt() {
        // given — ready on the third poll
        var job = crud.startJob(2);
        var events = new ArrayList<RunEvent>();

        // when
        var suite = suite("suites/poll-job.yaml");
        var result = BRAT.run(
                suite,
                environment(Map.of("jobId", job.get("id").asString(), "maxAttempts", "5")),
                List.of(events::add),
                new TestRunControl());

        // then — one request, three attempts, and the assertion sees the final response
        assertPassed(result, suite);
        var request = result.requestResults().getFirst();
        assertThat(request.status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(((RequestStatus.Completed) request.status()).numAttempts()).isEqualTo(3);

        // and — the request is still one node, with an attempt event inside it
        var attempts = events.stream().filter(AttemptFinished.class::isInstance).toList();
        assertThat(attempts).hasSize(3);
        assertThat(attempts)
                .extracting(event -> ((AttemptFinished) event).conditionMet())
                .containsExactly(false, false, true);
        assertThat(events.stream().filter(RunEvent.RequestStarted.class::isInstance))
                .hasSize(1);
    }

    @Test
    void run_givesUpWhenTheAttemptsRunOutAndStillRunsTheResponseActions() {
        // given — ready only on the sixth poll, and two attempts allowed
        var job = crud.startJob(5);

        // when
        var result = run("suites/poll-job.yaml", Map.of("jobId", job.get("id").asString(), "maxAttempts", "2"));

        // then — a give-up carries the last completed attempt and the author's message
        var request = result.requestResults().getFirst();
        assertThat(request.status()).isInstanceOf(RequestStatus.GaveUp.class);
        var gaveUp = (RequestStatus.GaveUp) request.status();
        assertThat(gaveUp.message()).contains("the job never became ready");
        assertThat(gaveUp.lastAttempt().numAttempts()).isEqualTo(2);

        // and — the response actions ran once, after the loop, against that last response
        assertThat(request.responseActionsResult().assertionResults()).hasSize(1);
        assertThat(request.responseActionsResult().assertionResults().getFirst().passed())
                .isFalse();
    }

    @Test
    void run_emitsNoAttemptEventForARequestThatDoesNotPoll() {
        // given
        var events = new ArrayList<RunEvent>();

        // when
        BRAT.run(
                suite("suites/status-code.yaml"),
                environment(Map.of("code", "200")),
                List.of(events::add),
                new TestRunControl());

        // then — a synthetic attempt would have to invent a condition nothing evaluated
        assertThat(events).noneMatch(AttemptFinished.class::isInstance);
    }
}
