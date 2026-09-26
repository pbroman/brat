package dev.pbroman.brat.core.runner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.api.handler.ResponseHandler;
import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.HttpResponseVars;
import dev.pbroman.brat.core.interpolation.configdata.ConditionInterpolator;
import dev.pbroman.brat.core.interpolation.configdata.RequestDefinitionInterpolators;
import dev.pbroman.brat.core.interpolation.configdata.RequestOptionsInterpolator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestProcessorTest {

    private static final RequestOptions OPTIONS = new RequestOptions(null);

    private Interpolation interpolation;
    private RequestDefinitionInterpolators requestDefinitionInterpolator;
    private ConfigDataInterpolator<Condition> conditionInterpolator;
    private ConditionResolver conditionResolver;
    private RequestHandler<RequestDefinition, Object> requestHandler;
    private ResponseHandler responseHandler;
    private ConfigDataInterpolator<FlowControl> flowControlInterpolator;
    private RequestProcessor underTest;
    private RuntimeData runtimeData;

    private final HttpRequestDefinition authored =
            new HttpRequestDefinition("${vars.baseUrl}/orders", "GET", null, null);
    private final HttpRequestDefinition interpolated =
            new HttpRequestDefinition("http://localhost/orders", "GET", null, null, Map.of());
    private final HttpResponse response = new HttpResponse(200, Map.of(), "{\"id\":\"42\"}");

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        interpolation = mock(Interpolation.class);
        requestDefinitionInterpolator = mock(RequestDefinitionInterpolators.class);
        conditionInterpolator = mock(ConfigDataInterpolator.class);
        conditionResolver = mock(ConditionResolver.class);
        requestHandler = mock(RequestHandler.class);
        // responseVars is a default method on the protocol interface, so a bare mock answers it with
        // an empty map and the namespace this test asserts on would be the mock's, not HTTP's.
        when(requestHandler.responseVars(any())).thenAnswer(call -> HttpResponseVars.of(call.getArgument(0)));
        responseHandler = mock(ResponseHandler.class);
        flowControlInterpolator = mock(ConfigDataInterpolator.class);
        var conditionEvaluator = new ConditionEvaluator(interpolation, conditionInterpolator, conditionResolver);
        underTest = new RequestProcessor(
                interpolation,
                requestDefinitionInterpolator,
                conditionEvaluator,
                responseHandler,
                flowControlInterpolator,
                new RequestOptionsInterpolator(),
                new RequestExecutor(conditionEvaluator, attempt -> {}));

        runtimeData = new RuntimeData(Map.of(), Map.of());

        when(requestDefinitionInterpolator.interpolated(any(), any(), any())).thenReturn(interpolated);
        when(requestHandler.performRequest(any(), any())).thenReturn(response);
        when(responseHandler.handleResponse(any(), any())).thenReturn(ResponseActionsResult.NONE);
    }

    private final RequestCoordinates coordinates =
            new RequestCoordinates("happy path/create an order", "create-order", "create an order", 3);

    private Request requestWith(Condition skipCondition, ResponseActions responseActions) {
        return new Request(
                "create an order",
                null,
                "create-order",
                null,
                skipCondition,
                null,
                null,
                authored,
                responseActions,
                null);
    }

    @Test
    void process_completesAndCarriesTheInterpolatedDefinition() {
        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status()).isInstanceOf(RequestStatus.Completed.class);
        assertThat(result.requestDefinition()).isSameAs(interpolated);
        assertThat(result.failed()).isFalse();
    }

    @Test
    void process_carriesTheCoordinatesItWasGiven() {
        // when - the path is the walk's to build, not this class's
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.coordinates()).isSameAs(coordinates);
    }

    @Test
    void process_setsTheRuntimeCursorFromTheCoordinates() {
        // when - so code called further down can record where it was without being handed the identity
        underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(runtimeData.getCurrentPath()).isEqualTo("happy path/create an order");
        assertThat(runtimeData.getCurrentRequestNo()).isEqualTo(3);
    }

    @Test
    void process_setsTheCursorBeforeAnythingCanFail() {
        // given - a tombstone written during response actions must carry this request's path
        when(requestDefinitionInterpolator.interpolated(any(), any(), any())).thenThrow(new BratException("nope"));

        // when
        underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(runtimeData.getCurrentPath()).isEqualTo("happy path/create an order");
    }

    @Test
    void process_throwsForNullCoordinates() {
        // when / then
        assertThatThrownBy(() -> underTest.process(requestWith(null, null), OPTIONS, null, runtimeData, requestHandler))
                .isInstanceOf(BratException.class);
    }

    @Test
    void process_reportsOneAttemptBecauseItDoesNotPoll() {
        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Completed.class))
                .extracting(RequestStatus.Completed::numAttempts)
                .isEqualTo(1);
    }

    @Test
    void process_makesTheResponseVisibleToTheResponseActions() {
        // given - assertions and captures read the response through the namespace, so it must be
        // populated while they run and only while they run
        var seen = new HashMap<String, Object>();
        when(responseHandler.handleResponse(any(), any())).thenAnswer(invocation -> {
            seen.putAll(runtimeData.getResponseVars());
            return ResponseActionsResult.NONE;
        });

        // when
        underTest.process(
                requestWith(null, new ResponseActions(List.of(), Map.of())),
                OPTIONS,
                coordinates,
                runtimeData,
                requestHandler);

        // then
        assertThat(seen).containsEntry("statusCode", 200);
    }

    @Test
    void process_leavesNoResponseVarsBehindAfterACompletedRequest() {
        // when
        underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - the response dies with the request; a later one cannot read it at all
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void process_snapshotsResponseVarsOntoTheStatus() {
        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - the runtime namespace is replaced by the next request, so the status holds a copy
        var completed = (RequestStatus.Completed) result.status();
        runtimeData.getResponseVars().clear();
        assertThat(completed.responseVars()).containsKey("statusCode");
    }

    @Test
    void process_carriesWhatTheResponseActionsProduced() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of());
        var produced = new ResponseActionsResult(List.of(), List.of());
        when(responseHandler.handleResponse(any(), any())).thenReturn(produced);

        // when
        var result = underTest.process(
                requestWith(null, responseActions), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.responseActionsResult()).isSameAs(produced);
    }

    @Test
    void process_runsNoResponseActionsWhenTheRequestDeclaresNone() {
        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        verify(responseHandler, never()).handleResponse(any(), any());
        assertThat(result.responseActionsResult()).isEqualTo(ResponseActionsResult.NONE);
    }

    @Test
    void process_skipsWhenTheSkipConditionHolds() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.skip}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenReturn(skipCondition);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - nothing else runs, and a skipped request has not failed
        assertThat(result.status()).isInstanceOf(RequestStatus.Skipped.class);
        assertThat(result.failed()).isFalse();
        verify(requestHandler, never()).performRequest(any(), any());
        verify(responseHandler, never()).handleResponse(any(), any());
    }

    @Test
    void process_runsWhenTheSkipConditionDoesNotHold() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.skip}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenReturn(skipCondition);
        when(conditionResolver.resolve(any())).thenReturn(false);

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status()).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void process_erroresWhenTheSkipConditionCannotBeInterpolated() {
        // given - a guard that cannot be evaluated must not fall open
        var skipCondition = new Condition("isTrue", "${vars.nope}");
        when(conditionInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new BratException("The variable 'nope' has not been set"));

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(result.failed()).isTrue();
        verify(requestHandler, never()).performRequest(any(), any());
    }

    @Test
    void process_namesTheSkipConditionInTheErrorMessage() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.nope}");
        when(conditionInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new BratException("The variable 'nope' has not been set"));

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - otherwise a reader is sent looking at the URL
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .containsIgnoringCase("skip condition");
    }

    @Test
    void process_carriesTheAuthoredDefinitionWhenTheSkipConditionFails() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.nope}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenThrow(new BratException("nope"));

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - the definition was never reached, so there is no interpolated copy
        assertThat(result.requestDefinition()).isSameAs(authored);
        verify(requestDefinitionInterpolator, never()).interpolated(any(), any(), any());
    }

    @Test
    void process_erroresWhenTheSkipConditionCannotBeResolved() {
        // given
        var skipCondition = new Condition("noSuchFunc", "x");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenReturn(skipCondition);
        when(conditionResolver.resolve(any())).thenThrow(new BratException("no rule for 'noSuchFunc'"));

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("noSuchFunc");
        assertThat(result.failed()).isTrue();
    }

    @Test
    void process_erroresWithTheAuthoredDefinitionWhenInterpolationFails() {
        // given
        when(requestDefinitionInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new BratException("The constant 'baseUrl' is not set."));

        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - there is no interpolated copy, so the authored one is what a reporter gets
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(result.requestDefinition()).isSameAs(authored);
        verify(requestHandler, never()).performRequest(any(), any());
    }

    @Test
    void process_erroresWithTheInterpolatedDefinitionWhenTheCallFails() {
        // given
        when(requestHandler.performRequest(any(), any())).thenThrow(new BratException("Connection refused"));

        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("Connection refused");
        assertThat(result.requestDefinition()).isSameAs(interpolated);
    }

    @Test
    void process_namesTheTypeOfAnUnplannedFailure() {
        // given - a defect in core or a plugin must not read as an authoring mistake
        when(requestHandler.performRequest(any(), any())).thenThrow(new IllegalStateException("broke"));

        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("IllegalStateException");
    }

    @Test
    void process_clearsResponseVarsWhenTheRequestErrors() {
        // given - an earlier request's response is still in the namespace
        runtimeData.getResponseVars().put("statusCode", 200);
        when(requestHandler.performRequest(any(), any())).thenThrow(new BratException("Connection refused"));

        // when
        underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - there must be no previous response rather than an old one: an assertion that passes
        // against some earlier request's response is worse than one that stops and says why
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void process_clearsResponseVarsWhenTheRequestIsSkipped() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.skip}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenReturn(skipCondition);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void process_erroresWhenTheResponseActionsThrow() {
        // given - ResponseHandler is an extension point, so core cannot assume an implementation keeps
        // its own failures in; a plugin that throws must become data on this request, not abort the run
        when(responseHandler.handleResponse(any(), any())).thenThrow(new IllegalStateException("a plugin broke"));

        // when
        var result = underTest.process(
                requestWith(null, new ResponseActions(List.of(), Map.of())),
                OPTIONS,
                coordinates,
                runtimeData,
                requestHandler);

        // then
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("IllegalStateException");
    }

    @Test
    void process_runsNoResponseActionsWhenTheRequestErrores() {
        // given
        var responseActions = new ResponseActions(List.of(), Map.of());
        when(requestHandler.performRequest(any(), any())).thenThrow(new BratException("Connection refused"));

        // when
        var result = underTest.process(
                requestWith(null, responseActions), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        verify(responseHandler, never()).handleResponse(any(), any());
        assertThat(result.responseActionsResult()).isEqualTo(ResponseActionsResult.NONE);
    }

    @Test
    void process_measuresElapsedMsOverTheWholeRequest() {
        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.elapsedMs()).isGreaterThanOrEqualTo(0);
    }

    // ---------- regressions found in review: every one of these was green under mocks ----------

    @Test
    void process_completesARequestDeclaringNoSkipCondition() {
        // given - the real interpolator, because a mock returns null for a null condition and hides
        // that the real one rejects it; every other test here mocks it, which is how this got through
        var realEvaluator = new ConditionEvaluator(interpolation, new ConditionInterpolator(), conditionResolver);
        var underTestWithRealInterpolator = new RequestProcessor(
                interpolation,
                requestDefinitionInterpolator,
                realEvaluator,
                responseHandler,
                flowControlInterpolator,
                new RequestOptionsInterpolator(),
                new RequestExecutor(realEvaluator, attempt -> {}));

        // when
        var result = underTestWithRealInterpolator.process(
                requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - a request with no skip condition is the ordinary case, not an error
        assertThat(result.status()).isInstanceOf(RequestStatus.Completed.class);
    }

    @Test
    void process_clearsResponseVarsWhenTheSkipConditionCannotBeInterpolated() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.nope}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenThrow(new BratException("nope"));

        // when
        underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - an errored request has no response, so nothing may read the one before it
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void process_clearsResponseVarsWhenTheDefinitionCannotBeInterpolated() {
        // given
        when(requestDefinitionInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new BratException("The constant 'baseUrl' is not set."));

        // when
        underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(runtimeData.getResponseVars()).isEmpty();
    }

    @Test
    void process_erroresWhenSkipConditionInterpolationThrowsAnUnplannedException() {
        // given - interpolation rules are an extension point, so core cannot enumerate what arrives
        var skipCondition = new Condition("isTrue", "${plugin.x}");
        when(conditionInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new IllegalStateException("a plugin rule broke"));

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - it becomes data on this request rather than aborting the run
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("IllegalStateException");
    }

    @Test
    void process_erroresWhenDefinitionInterpolationThrowsAnUnplannedException() {
        // given
        when(requestDefinitionInterpolator.interpolated(any(), any(), any()))
                .thenThrow(new IllegalStateException("a plugin rule broke"));

        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("IllegalStateException");
    }

    @Test
    void process_measuresElapsedMsWhenTheSkipConditionFails() {
        // given - a slow failure, so a hard-coded zero cannot pass
        var skipCondition = new Condition("isTrue", "${vars.nope}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenAnswer(invocation -> {
            Thread.sleep(10);
            throw new BratException("nope");
        });

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.elapsedMs()).isPositive();
    }

    @Test
    void process_runsADefinitionOfAnyProtocolItIsGivenAHandlerFor() {
        // given - this class no longer knows what HTTP is: the caller selected a handler, and a
        // definition of any protocol with an interpolator for it runs like any other
        RequestDefinition ftp = () -> "ftp";
        var request = new Request("fetch the order", null, null, null, null, null, null, ftp, null, null);
        when(requestDefinitionInterpolator.interpolated(any(), any(), any())).thenReturn(ftp);

        // when
        var result = underTest.process(request, OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.status()).isInstanceOf(RequestStatus.Completed.class);
    }

    // ---------- request options ----------

    @Test
    void process_handsTheInterpolatedOptionsToTheHandler() {
        // given
        when(interpolation.outcome(eq("${vars.timeout}"), any())).thenReturn(new InterpolationOutcome("750", "750"));
        var options = new RequestOptions("${vars.timeout}");
        var handed = ArgumentCaptor.forClass(RequestOptions.class);

        // when
        underTest.process(requestWith(null, null), options, coordinates, runtimeData, requestHandler);

        // then
        verify(requestHandler).performRequest(eq(interpolated), handed.capture());
        assertThat(handed.getValue().isInterpolated()).isTrue();
        assertThat(handed.getValue().timeoutMs()).isEqualTo(750L);
    }

    @Test
    void process_carriesTheInterpolatedOptions() {
        // given
        when(interpolation.outcome(eq("${vars.timeout}"), any())).thenReturn(new InterpolationOutcome("750", "750"));

        // when
        var result = underTest.process(
                requestWith(null, null),
                new RequestOptions("${vars.timeout}"),
                coordinates,
                runtimeData,
                requestHandler);

        // then
        assertThat(result.requestOptions().isInterpolated()).isTrue();
        assertThat(result.requestOptions().getTimeout()).isEqualTo("750");
    }

    @Test
    void process_erroresWithoutPerformingWhenTheOptionsDoNotValidate() {
        // given - a timeout that resolves to something that is not a number of milliseconds
        when(interpolation.outcome(eq("${vars.timeout}"), any())).thenReturn(new InterpolationOutcome("soon", "soon"));
        var options = new RequestOptions("${vars.timeout}");

        // when
        var result = underTest.process(requestWith(null, null), options, coordinates, runtimeData, requestHandler);

        // then - the definition got through, the options did not
        assertThat(result.status())
                .asInstanceOf(type(RequestStatus.Errored.class))
                .extracting(RequestStatus.Errored::message)
                .asString()
                .contains("soon");
        assertThat(result.requestDefinition()).isSameAs(interpolated);
        assertThat(result.requestOptions()).isSameAs(options);
        verify(requestHandler, never()).performRequest(any(), any());
    }

    @Test
    void process_carriesTheAuthoredOptionsWhenTheSkipConditionFails() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.nope}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenThrow(new BratException("nope"));

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - the options were never reached
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(result.requestOptions()).isSameAs(OPTIONS);
    }

    @Test
    void process_carriesTheAuthoredOptionsWhenTheDefinitionFailsToInterpolate() {
        // given
        when(requestDefinitionInterpolator.interpolated(any(), any(), any())).thenThrow(new BratException("nope"));

        // when
        var result = underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then - options are interpolated after the definition, so they were never reached either
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(result.requestOptions()).isSameAs(OPTIONS);
    }

    @Test
    void process_carriesTheInterpolatedOptionsWhenFlowControlFails() {
        // given
        when(flowControlInterpolator.interpolated(any(), any(), any())).thenThrow(new BratException("nope"));
        var request = new Request(
                "create an order",
                null,
                "create-order",
                null,
                null,
                null,
                null,
                authored,
                null,
                new FlowControl("${vars.wait}", null));

        // when
        var result = underTest.process(request, OPTIONS, coordinates, runtimeData, requestHandler);

        // then - the options got through before flow control failed
        assertThat(result.status()).isInstanceOf(RequestStatus.Errored.class);
        assertThat(result.requestOptions()).isNotSameAs(OPTIONS);
        assertThat(result.requestOptions().isInterpolated()).isTrue();
    }

    @Test
    void process_carriesTheAuthoredOptionsWhenSkipped() {
        // given
        var skipCondition = new Condition("isTrue", "${vars.skip}");
        when(conditionInterpolator.interpolated(any(), any(), any())).thenReturn(skipCondition);
        when(conditionResolver.resolve(any())).thenReturn(true);

        // when
        var result =
                underTest.process(requestWith(skipCondition, null), OPTIONS, coordinates, runtimeData, requestHandler);

        // then
        assertThat(result.requestOptions()).isSameAs(OPTIONS);
    }

    @Test
    void process_throwsForNullOptions() {
        // when / then - structural: the caller always has options to give, even empty ones
        assertThatThrownBy(() ->
                        underTest.process(requestWith(null, null), null, coordinates, runtimeData, requestHandler))
                .isInstanceOf(BratException.class);
    }

    @Test
    void process_throwsForANullHandler() {
        // when / then - structural: nothing can perform the request, which is not this request failing
        assertThatThrownBy(() -> underTest.process(requestWith(null, null), OPTIONS, coordinates, runtimeData, null))
                .isInstanceOf(BratException.class);
    }

    @Test
    void process_throwsForANullRequest() {
        // when / then - structural, so it is not converted to a result
        assertThatThrownBy(() -> underTest.process(null, OPTIONS, coordinates, runtimeData, requestHandler))
                .isInstanceOf(BratException.class);
    }

    @Test
    void process_throwsForNullRuntimeData() {
        // when / then
        assertThatThrownBy(() -> underTest.process(requestWith(null, null), OPTIONS, coordinates, null, requestHandler))
                .isInstanceOf(BratException.class);
    }

    @Test
    void process_throwsWhenTheRequestDeclaresNoDefinition() {
        // given
        var request = new Request("create an order", null, null, null, null, null, null, null, null, null);

        // when / then
        assertThatThrownBy(() -> underTest.process(request, OPTIONS, coordinates, runtimeData, requestHandler))
                .isInstanceOf(BratException.class);
    }
}
