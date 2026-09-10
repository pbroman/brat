package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestExecutorTest {

    private HttpRequestHandler requestHandler;
    private ConditionEvaluator conditionEvaluator;
    private RequestExecutor underTest;
    private RuntimeData runtimeData;
    private final List<AttemptFinished> attempts = new ArrayList<>();

    private final Condition pollCondition = new Condition("isEqualTo", "${response.statusCode}", "200");
    private final HttpRequestDefinition definition =
            new HttpRequestDefinition("http://x/jobs/1", "GET", null, null, null, null, Map.of());
    private final RequestCoordinates coordinates = new RequestCoordinates("s/poll", null, "poll", 1);

    @BeforeEach
    void setUp() {
        requestHandler = mock(HttpRequestHandler.class);
        conditionEvaluator = mock(ConditionEvaluator.class);
        attempts.clear();
        underTest = new RequestExecutor(requestHandler, conditionEvaluator, attempts::add);
        runtimeData = new RuntimeData(Map.of(), Map.of());
    }

    /** Bounds as {@link PollBounds} would produce them, so a test states only what it varies. */
    private Optional<PollBounds> bounds(String maxAttempts, String wait, String messageOnFail) {
        return PollBounds.of(new FlowControl(null, new RepeatUntil(pollCondition, maxAttempts, wait, messageOnFail)));
    }

    private HttpResponse response(int statusCode) {
        return new HttpResponse(statusCode, Map.of(), "{}");
    }

    /** Stubs the evaluator's verdicts in order; the interpolated condition is the same throughout. */
    private void conditionHolds(Boolean first, Boolean... rest) {
        when(conditionEvaluator.evaluate(any(), any()))
                .thenReturn(
                        new ConditionEvaluator.Evaluation(pollCondition, first),
                        java.util.Arrays.stream(rest)
                                .map(held -> new ConditionEvaluator.Evaluation(pollCondition, held))
                                .toArray(ConditionEvaluator.Evaluation[]::new));
    }

    // ---------- without bounds ----------

    @Test
    void execute_performsOneAttemptWhenThereAreNoBounds() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(200));

        // when
        var status = underTest.execute(definition, Optional.empty(), coordinates, runtimeData);

        // then - no loop, so no condition is consulted and no progress is reported within one
        assertThat(status).isInstanceOf(RequestStatus.Completed.class);
        verify(requestHandler, times(1)).performRequest(any());
        verify(conditionEvaluator, never()).evaluate(any(), any());
        assertThat(attempts).isEmpty();
    }

    @Test
    void execute_erroresWhenAnUnpolledRequestCannotReachTheServer() {
        // given
        when(requestHandler.performRequest(any())).thenThrow(new BratException("Connection refused"));

        // when
        var status = underTest.execute(definition, Optional.empty(), coordinates, runtimeData);

        // then
        assertThat(status)
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("Connection refused");
    }

    @Test
    void execute_publishesTheResponseSoLaterStepsCanReadIt() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(201));

        // when
        underTest.execute(definition, Optional.empty(), coordinates, runtimeData);

        // then - the assertions and captures read the response through the namespace
        assertThat(runtimeData.getResponseVars()).containsEntry("statusCode", 201);
    }

    // ---------- how the loop ends ----------

    @Test
    void execute_stopsAsSoonAsTheConditionHolds() {
        // given - the third attempt is the one that matches
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(202), response(200));
        conditionHolds(false, false, true);

        // when
        var status = underTest.execute(definition, bounds("5", "0", null), coordinates, runtimeData);

        // then
        assertThat(status)
                .asInstanceOf(type(RequestStatus.Completed.class))
                .extracting(RequestStatus.Completed::numAttempts)
                .isEqualTo(3);
        verify(requestHandler, times(3)).performRequest(any());
    }

    @Test
    void execute_completesWhenTheConditionHoldsAfterEarlierAttemptsErrored() {
        // given - the archetypal poll: nothing is listening yet, then it comes up
        when(requestHandler.performRequest(any()))
                .thenThrow(new BratException("Connection refused"))
                .thenReturn(response(200));
        conditionHolds(true);

        // when
        var status = underTest.execute(definition, bounds("5", "0", null), coordinates, runtimeData);

        // then - an errored attempt is a retry, so earlier failures do not decide the outcome
        assertThat(status).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void execute_givesUpWhenTheAttemptsAreExhaustedWithResponses() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        conditionHolds(false);

        // when
        var status = underTest.execute(definition, bounds("3", "0", "still processing"), coordinates, runtimeData);

        // then - never reaching the condition is a failure, not a pass
        assertThat(status).asInstanceOf(type(RequestStatus.GaveUp.class)).satisfies(gaveUp -> {
            assertThat(gaveUp.message()).isEqualTo("still processing");
            assertThat(gaveUp.lastAttempt().numAttempts()).isEqualTo(3);
            assertThat(gaveUp.lastAttempt().responseVars()).containsEntry("statusCode", 202);
        });
    }

    @Test
    void execute_erroresWhenTheAttemptsAreExhaustedAndTheLastAttemptErrored() {
        // given - responses early, a connection failure at the end
        when(requestHandler.performRequest(any()))
                .thenReturn(response(202))
                .thenThrow(new BratException("Connection refused"));
        conditionHolds(false);

        // when
        var status = underTest.execute(definition, bounds("2", "0", "still processing"), coordinates, runtimeData);

        // then - messageOnFail describes a condition that never came true, not a connection failure
        assertThat(status)
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("Connection refused")
                .doesNotContain("still processing");
    }

    @Test
    void execute_countsAnErroredAttemptAgainstTheBudget() {
        // given - every attempt fails to reach the server
        when(requestHandler.performRequest(any())).thenThrow(new BratException("Connection refused"));

        // when
        underTest.execute(definition, bounds("3", "0", null), coordinates, runtimeData);

        // then - the budget bounds errored attempts too, or a dead host loops forever
        verify(requestHandler, times(3)).performRequest(any());
    }

    @Test
    void execute_doesNotEvaluateTheConditionAfterAnErroredAttempt() {
        // given
        when(requestHandler.performRequest(any())).thenThrow(new BratException("Connection refused"));

        // when
        underTest.execute(definition, bounds("2", "0", null), coordinates, runtimeData);

        // then - there is no response, so ${response.*} has nothing to resolve against
        verify(conditionEvaluator, never()).evaluate(any(), any());
    }

    @Test
    void execute_erroresImmediatelyWhenTheConditionCannotBeEvaluated() {
        // given - a typo'd func is an authoring error, not a transient one
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionEvaluator.evaluate(any(), any())).thenThrow(new BratException("No rule recognizes 'isEqaulTo'"));

        // when
        var status = underTest.execute(definition, bounds("5", "0", null), coordinates, runtimeData);

        // then - retrying it five times with waits cannot make it start working
        assertThat(status).isInstanceOf(RequestStatus.Errored.class);
        verify(requestHandler, times(1)).performRequest(any());
    }

    // ---------- waiting ----------

    @Test
    void execute_waitsBetweenAttempts() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        conditionHolds(false);

        // when - three attempts means two waits
        long start = System.currentTimeMillis();
        underTest.execute(definition, bounds("3", "20", null), coordinates, runtimeData);

        // then - a lower bound only. An upper bound cannot distinguish two waits from three here:
        // measured, a cold JVM adds ~330ms to this test, far more than any wait worth using.
        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(40);
    }

    @Test
    void execute_doesNotWaitAfterTheFinalAttempt() {
        // given - a one-attempt poll that will give up. The interrupt flag is a deterministic probe
        // for "did it sleep at all": a sleep that happens throws immediately, one that never happens
        // is silent - which wall-clock timing cannot tell apart at this scale
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        conditionHolds(false);

        try {
            Thread.currentThread().interrupt();

            // when
            var status = underTest.execute(definition, bounds("1", "100", null), coordinates, runtimeData);

            // then - it reached the give-up without ever waiting; pausing after the final attempt
            // would only delay a give-up that has already been decided
            assertThat(status).isInstanceOf(RequestStatus.GaveUp.class);
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void execute_propagatesAnInterruptRatherThanRecordingIt() {
        // given - an interrupt is cancellation of the run, not a failure of this request
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        conditionHolds(false);
        var pollBounds = bounds("3", "1000", null);

        try {
            Thread.currentThread().interrupt();

            // when / then
            assertThatThrownBy(() -> underTest.execute(definition, pollBounds, coordinates, runtimeData))
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("Interrupted");

            // then - the flag is restored, so a caller's own interrupt checks still see it
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    // ---------- events ----------

    @Test
    void execute_emitsOneAttemptFinishedPerAttempt() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(202), response(200));
        conditionHolds(false, false, true);

        // when
        underTest.execute(definition, bounds("5", "0", null), coordinates, runtimeData);

        // then - a poll must be visible while it runs, not only when it ends
        assertThat(attempts).hasSize(3);
        assertThat(attempts).extracting(AttemptFinished::attempt).containsExactly(1, 2, 3);
        assertThat(attempts).allSatisfy(event -> {
            assertThat(event.coordinates()).isEqualTo(coordinates);
            assertThat(event.maxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void execute_marksOnlyTheFinalAttemptAsConditionMet() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(200));
        conditionHolds(false, true);

        // when
        underTest.execute(definition, bounds("5", "0", null), coordinates, runtimeData);

        // then
        assertThat(attempts).extracting(AttemptFinished::conditionMet).containsExactly(false, true);
    }

    @Test
    void execute_carriesTheCauseOnAnErroredAttemptFinished() {
        // given
        when(requestHandler.performRequest(any()))
                .thenThrow(new BratException("Connection refused"))
                .thenReturn(response(200));
        conditionHolds(true);

        // when
        underTest.execute(definition, bounds("5", "0", null), coordinates, runtimeData);

        // then - thirty identical failures must look different from silence
        assertThat(attempts.getFirst().error()).contains("Connection refused");
        assertThat(attempts.getFirst().conditionMet()).isFalse();
        assertThat(attempts.getLast().error()).isNull();
    }

    @Test
    void execute_reportsTheFinalAttemptsRoundTripTimeNotTheWholePoll() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(200));
        conditionHolds(false, true);

        // when
        long start = System.currentTimeMillis();
        var status = underTest.execute(definition, bounds("5", "50", null), coordinates, runtimeData);

        // then - an aggregate over response times must not pick up the waits between attempts
        var completed = (RequestStatus.Completed) status;
        assertThat(completed.roundTripTimeMs()).isLessThan(System.currentTimeMillis() - start);
    }
}
