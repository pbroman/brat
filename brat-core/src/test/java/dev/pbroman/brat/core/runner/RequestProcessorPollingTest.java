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
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the processor decides <em>around</em> a poll: that the bounds reach it, that a bad one errors
 * the request rather than escaping, and that the response actions run against the attempt it ended
 * on. How the loop itself behaves is {@link RequestExecutor}'s, and is tested there.
 */
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

        var interpolation = mock(Interpolation.class);
        var conditionEvaluator = new ConditionEvaluator(interpolation, conditionInterpolator, conditionResolver);
        underTest = new RequestProcessor(
                interpolation,
                requestDefinitionInterpolator,
                conditionEvaluator,
                responseHandler,
                flowControlInterpolator,
                new RequestExecutor(requestHandler, conditionEvaluator, attempts::add));

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
                .contains("must be a positive number");
        verify(requestHandler, never()).performRequest(any());
    }
}
