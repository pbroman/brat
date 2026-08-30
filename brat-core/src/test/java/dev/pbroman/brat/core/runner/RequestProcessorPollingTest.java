package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
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

class RequestProcessorPollingTest {

    private ConfigDataInterpolator<HttpRequestDefinition> requestDefinitionInterpolator;
    private ConfigDataInterpolator<Condition> conditionInterpolator;
    private ConfigDataInterpolator<FlowControl> flowControlInterpolator;
    private ConditionResolver conditionResolver;
    private HttpRequestHandler requestHandler;
    private ResponseHandler responseHandler;
    private RequestProcessor underTest;
    private RuntimeData runtimeData;
    private final List<AttemptFinished> attempts = new ArrayList<>();

    private final Condition pollCondition = new Condition("isEqualTo", "${response.statusCode}", "200");
    private final HttpRequestDefinition authored =
            new HttpRequestDefinition("http://x/jobs/1", "GET", null, null, null, null);
    private final HttpRequestDefinition interpolated =
            new HttpRequestDefinition("http://x/jobs/1", "GET", null, null, null, null, Map.of());
    private final RequestCoordinates coordinates = new RequestCoordinates("s/poll", null, "poll", 1);

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        requestDefinitionInterpolator = mock(ConfigDataInterpolator.class);
        conditionInterpolator = mock(ConfigDataInterpolator.class);
        flowControlInterpolator = mock(ConfigDataInterpolator.class);
        conditionResolver = mock(ConditionResolver.class);
        requestHandler = mock(HttpRequestHandler.class);
        responseHandler = mock(ResponseHandler.class);
        attempts.clear();

        underTest = new RequestProcessor(
                mock(Interpolation.class),
                requestDefinitionInterpolator,
                conditionInterpolator,
                conditionResolver,
                requestHandler,
                responseHandler,
                flowControlInterpolator,
                attempts::add);

        runtimeData = new RuntimeData(Map.of(), Map.of());
        when(requestDefinitionInterpolator.interpolated(any(), any(), any())).thenReturn(interpolated);
        when(responseHandler.handleResponse(any(), any())).thenReturn(ResponseActionsResult.NONE);
    }

    /** A polling request whose loop is bounded by {@code maxAttempts} and paced by {@code wait}. */
    private Request polling(String maxAttempts, String wait, String messageOnFail) {
        var repeatUntil = new RepeatUntil(pollCondition, maxAttempts, wait, messageOnFail);
        var flowControl = new FlowControl(null, repeatUntil);
        when(flowControlInterpolator.interpolated(any(), any(), any())).thenReturn(flowControl);
        return new Request("poll", null, null, null, null, null, authored, null, flowControl);
    }

    /** A polling request that also declares response actions, so the two can be observed together. */
    private Request pollingWithResponseActions(String maxAttempts, String messageOnFail) {
        var repeatUntil = new RepeatUntil(pollCondition, maxAttempts, "0", messageOnFail);
        var flowControl = new FlowControl(null, repeatUntil);
        when(flowControlInterpolator.interpolated(any(), any(), any())).thenReturn(flowControl);
        return new Request(
                "poll", null, null, null, null, null, authored, new ResponseActions(List.of(), Map.of()), flowControl);
    }

    private HttpResponse response(int statusCode) {
        return new HttpResponse(statusCode, Map.of(), "{}");
    }

    // ---------- how the loop ends ----------

    @Test
    void process_stopsAsSoonAsTheConditionHolds() {
        // given - the third attempt is the one that matches
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(202), response(200));
        when(conditionResolver.resolve(any())).thenReturn(false, false, true);

        // when
        var result = underTest.process(polling("5", "0", null), coordinates, runtimeData);

        // then
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.Completed.class))
                .extracting(RequestStatus.Completed::numAttempts)
                .isEqualTo(3);
        verify(requestHandler, times(3)).performRequest(any());
    }

    @Test
    void process_completesWhenTheConditionHoldsAfterEarlierAttemptsErrored() {
        // given - the archetypal poll: nothing is listening yet, then it comes up
        when(requestHandler.performRequest(any()))
                .thenThrow(new BratException("Connection refused"))
                .thenReturn(response(200));
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result = underTest.process(polling("5", "0", null), coordinates, runtimeData);

        // then - an errored attempt is a retry, so earlier failures do not decide the outcome
        assertThat(result.status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(result.failed()).isFalse();
    }

    @Test
    void process_givesUpWhenTheAttemptsAreExhaustedWithResponses() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result = underTest.process(polling("3", "0", "still processing"), coordinates, runtimeData);

        // then - never reaching the condition is a failure, not a pass
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.GaveUp.class))
                .satisfies(gaveUp -> {
                    assertThat(gaveUp.message()).contains("still processing");
                    assertThat(gaveUp.lastAttempt().numAttempts()).isEqualTo(3);
                    assertThat(gaveUp.lastAttempt().responseVars()).containsEntry("statusCode", 202);
                });
        assertThat(result.failed()).isTrue();
    }

    @Test
    void process_generatesAGiveUpMessageWhenTheAuthorDeclaredNone() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result = underTest.process(polling("2", "0", null), coordinates, runtimeData);

        // then - never null: a report needs a sentence even where the author wrote none
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.GaveUp.class))
                .extracting(RequestStatus.GaveUp::message)
                .asString()
                .isNotEmpty();
    }

    @Test
    void process_erroresWhenTheAttemptsAreExhaustedAndTheLastAttemptErrored() {
        // given - responses early, a connection failure at the end
        when(requestHandler.performRequest(any()))
                .thenReturn(response(202))
                .thenThrow(new BratException("Connection refused"));
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result = underTest.process(polling("2", "0", "still processing"), coordinates, runtimeData);

        // then - messageOnFail describes a condition that never came true, not a connection failure
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("Connection refused")
                .doesNotContain("still processing");
    }

    @Test
    void process_countsAnErroredAttemptAgainstTheBudget() {
        // given - every attempt fails to reach the server
        when(requestHandler.performRequest(any())).thenThrow(new BratException("Connection refused"));

        // when
        underTest.process(polling("3", "0", null), coordinates, runtimeData);

        // then - the budget bounds errored attempts too, or a dead host loops forever
        verify(requestHandler, times(3)).performRequest(any());
    }

    @Test
    void process_doesNotEvaluateTheConditionAfterAnErroredAttempt() {
        // given
        when(requestHandler.performRequest(any())).thenThrow(new BratException("Connection refused"));

        // when
        underTest.process(polling("2", "0", null), coordinates, runtimeData);

        // then - there is no response, so ${response.*} has nothing to resolve against
        verify(conditionResolver, never()).resolve(any());
    }

    @Test
    void process_erroresImmediatelyWhenTheLoopConditionCannotBeEvaluated() {
        // given - a typo'd func is an authoring error, not a transient one
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenThrow(new BratException("No rule recognizes 'isEqaulTo'"));

        // when
        var result = underTest.process(polling("5", "0", null), coordinates, runtimeData);

        // then - retrying it five times with waits cannot make it start working
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        verify(requestHandler, times(1)).performRequest(any());
    }

    // ---------- bounds ----------

    @Test
    void process_defaultsMaxAttemptsWhenTheAuthorDeclaredNone() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        underTest.process(polling(null, "0", null), coordinates, runtimeData);

        // then - a bail you have to remember to write is not a bail
        verify(requestHandler, times(3)).performRequest(any());
    }

    @Test
    void process_erroresWhenMaxAttemptsIsUnparseable() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));

        // when
        var result = underTest.process(polling("soon", "0", null), coordinates, runtimeData);

        // then
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        verify(requestHandler, never()).performRequest(any());
    }

    @Test
    void process_erroresWhenTheFlowControlCannotBeInterpolated() {
        // given
        var request = polling("3", "0", null);
        when(flowControlInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new BratException("The parameter 'attempts' is not set."));

        // when
        var result = underTest.process(request, coordinates, runtimeData);

        // then - the loop's bounds are unknowable, and guessing them is how a suite spins
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        verify(requestHandler, never()).performRequest(any());
    }

    @Test
    void process_waitsBetweenAttempts() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when - three attempts means two waits
        var result = underTest.process(polling("3", "20", null), coordinates, runtimeData);

        // then
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(40);
    }

    // ---------- what the numbers mean ----------

    @Test
    void process_reportsTheFinalAttemptsRoundTripTimeNotTheWholePoll() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(200));
        when(conditionResolver.resolve(any())).thenReturn(false, true);

        // when
        var result = underTest.process(polling("5", "50", null), coordinates, runtimeData);

        // then - an aggregate over response times must not pick up the waits between attempts
        var completed = (RequestStatus.Completed) result.status();
        assertThat(completed.roundTripTimeMs()).isLessThan(result.elapsedMs());
    }

    // ---------- events ----------

    @Test
    void process_emitsOneAttemptFinishedPerAttempt() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(202), response(200));
        when(conditionResolver.resolve(any())).thenReturn(false, false, true);

        // when
        underTest.process(polling("5", "0", null), coordinates, runtimeData);

        // then - a poll must be visible while it runs, not only when it ends
        assertThat(attempts).hasSize(3);
        assertThat(attempts).extracting(AttemptFinished::attempt).containsExactly(1, 2, 3);
        assertThat(attempts).allSatisfy(event -> {
            assertThat(event.coordinates()).isEqualTo(coordinates);
            assertThat(event.maxAttempts()).isEqualTo(5);
        });
    }

    @Test
    void process_marksOnlyTheFinalAttemptAsConditionMet() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(202), response(200));
        when(conditionResolver.resolve(any())).thenReturn(false, true);

        // when
        underTest.process(polling("5", "0", null), coordinates, runtimeData);

        // then
        assertThat(attempts).extracting(AttemptFinished::conditionMet).containsExactly(false, true);
    }

    @Test
    void process_carriesTheCauseOnAnErroredAttemptFinished() {
        // given
        when(requestHandler.performRequest(any()))
                .thenThrow(new BratException("Connection refused"))
                .thenReturn(response(200));
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        underTest.process(polling("5", "0", null), coordinates, runtimeData);

        // then - thirty identical failures must look different from silence
        assertThat(attempts.getFirst().error()).contains("Connection refused");
        assertThat(attempts.getFirst().conditionMet()).isFalse();
        assertThat(attempts.getLast().error()).isNull();
    }

    @Test
    void process_emitsNoAttemptFinishedForARequestThatDoesNotPoll() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(200));
        var request = new Request("once", null, null, null, null, null, authored, null, null);

        // when
        underTest.process(request, coordinates, runtimeData);

        // then - no loop, so no progress to report within one
        assertThat(attempts).isEmpty();
    }

    // ---------- response actions ----------

    @Test
    void process_runsResponseActionsOnAGiveUp() {
        // given - there is a response, and "gave up and the status was 500" says more than either
        when(requestHandler.performRequest(any())).thenReturn(response(500));
        when(conditionResolver.resolve(any())).thenReturn(false);
        var repeatUntil = new RepeatUntil(pollCondition, "2", "0", "gave up");
        var flowControl = new FlowControl(null, repeatUntil);
        when(flowControlInterpolator.interpolated(any(), any(), any())).thenReturn(flowControl);
        var request = new Request(
                "poll", null, null, null, null, null, authored, new ResponseActions(List.of(), Map.of()), flowControl);

        // when
        underTest.process(request, coordinates, runtimeData);

        // then
        verify(responseHandler).handleResponse(any(), any());
    }

    @Test
    void process_runsResponseActionsWhenTheConditionHolds() {
        // given - the other half of "response actions run on Completed and on GaveUp"
        when(requestHandler.performRequest(any())).thenReturn(response(200));
        when(conditionResolver.resolve(any())).thenReturn(true);
        var request = pollingWithResponseActions("3", null);

        // when
        var result = underTest.process(request, coordinates, runtimeData);

        // then
        verify(responseHandler).handleResponse(any(), any());
        assertThat(result.status()).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void process_erroresWhenTheResponseActionsThrowOnAGiveUp() {
        // given - ResponseHandler is an extension point, so core cannot assume an implementation
        // keeps its own failures in; a plugin that throws must not abort the run
        when(requestHandler.performRequest(any())).thenReturn(response(500));
        when(conditionResolver.resolve(any())).thenReturn(false);
        when(responseHandler.handleResponse(any(), any())).thenThrow(new IllegalStateException("a plugin broke"));
        var request = pollingWithResponseActions("2", "gave up");

        // when
        var result = underTest.process(request, coordinates, runtimeData);

        // then
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("IllegalStateException");
    }

    @Test
    void process_erroresWhenMaxAttemptsIsNotPositive() {
        // given - a ceiling of zero is a ceiling the author did not mean, and ${params.attempts}
        // resolving to 0 is the realistic route in
        when(requestHandler.performRequest(any())).thenReturn(response(200));

        // when
        var result = underTest.process(polling("0", "0", null), coordinates, runtimeData);

        // then - the same treatment an unparseable value gets, rather than a silent default
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("non-positive");
        verify(requestHandler, never()).performRequest(any());
    }

    @Test
    void process_erroresWhenWaitBetweenAttemptsIsUnparseable() {
        // given - the same treatment maxAttempts gets, rather than a warning and no wait
        when(requestHandler.performRequest(any())).thenReturn(response(200));

        // when
        var result = underTest.process(polling("3", "soon", null), coordinates, runtimeData);

        // then
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        verify(requestHandler, never()).performRequest(any());
    }

    @Test
    void process_erroresWhenWaitBetweenAttemptsIsNegative() {
        // given
        when(requestHandler.performRequest(any())).thenReturn(response(200));

        // when
        var result = underTest.process(polling("3", "-1", null), coordinates, runtimeData);

        // then - zero is a legitimate "no wait"; below zero is not a pace
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("negative");
        verify(requestHandler, never()).performRequest(any());
    }

    @Test
    void process_propagatesAnInterruptRatherThanRecordingIt() {
        // given - an interrupt is cancellation of the run, not a failure of this request. Setting the
        // flag before the call makes the first Thread.sleep throw at once, so no second thread and no
        // timing are involved
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenReturn(false);
        var request = polling("3", "1000", null);

        try {
            Thread.currentThread().interrupt();

            // when / then
            assertThatThrownBy(() -> underTest.process(request, coordinates, runtimeData))
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("Interrupted");

            // then - the flag is restored, so a caller's own interrupt checks still see it
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void process_doesNotWaitWhenNoWaitBetweenAttemptsIsDeclared() {
        // given - a poll that exhausts three attempts with no declared pace
        when(requestHandler.performRequest(any())).thenReturn(response(202));
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result = underTest.process(polling("3", null, null), coordinates, runtimeData);

        // then - the paired process_waitsBetweenAttempts pins the positive case; this one pins that
        // an absent wait is no wait rather than an invented default
        assertThat(result.status()).isInstanceOf(RequestStatus.GaveUp.class);
        assertThat(result.elapsedMs()).isLessThan(500);
    }
}
