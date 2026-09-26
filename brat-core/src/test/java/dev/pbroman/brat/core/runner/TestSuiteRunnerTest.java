package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSuiteRunnerTest {

    private final RuntimeData runtimeData = new RuntimeData(Map.of(), Map.of());
    private final List<RunEvent> events = new ArrayList<>();
    private final List<RequestResult> results = new ArrayList<>();
    private final FlagRunControl runControl = new FlagRunControl();

    private RequestProcessor processor;
    private ProtocolRegistry protocolRegistry;
    private RequestHandler<RequestDefinition, Object> handler;

    /** Cancels the run when an event matching it is emitted, the way a listener would. */
    private Predicate<RunEvent> cancelOn = event -> false;

    private TestSuiteRunner underTest;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        processor = mock(RequestProcessor.class);
        protocolRegistry = mock(ProtocolRegistry.class);
        handler = mock(RequestHandler.class);
        when(protocolRegistry.resolve(any(), any())).thenReturn(handler);
        when(processor.process(any(), any(), any(), any(), any()))
                .thenAnswer(call -> new RequestResult(
                        call.getArgument(2),
                        ((Request) call.getArgument(0)).requestDefinition(),
                        call.getArgument(1),
                        new RequestStatus.Completed(Map.of(), 1, 1),
                        1,
                        null));
        underTest = new TestSuiteRunner(
                processor,
                protocolRegistry,
                event -> {
                    events.add(event);
                    if (cancelOn.test(event)) {
                        runControl.cancel();
                    }
                },
                results::add,
                runControl);
    }

    // ---------- order ----------

    @Test
    void walk_bracketsASuiteWithItsEnteredAndExitedEvents() {
        // given
        var suite = suite("s", Phase.MAIN, List.of(request("r", Phase.MAIN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/r", "finish s/r", "exit s Completed");
    }

    @Test
    void walk_runsTheFiveStepsInOrderWhateverTheDeclarationOrder() {
        // given - every category declared in the opposite order to the one it runs in
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(
                        request("teardownRequest", Phase.TEARDOWN),
                        request("mainRequest", Phase.MAIN),
                        request("setupRequest", Phase.SETUP)),
                List.of(
                        suite("teardownSuite", Phase.TEARDOWN, List.of(), List.of()),
                        suite("mainSuite", Phase.MAIN, List.of(), List.of()),
                        suite("setupSuite", Phase.SETUP, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder())
                .containsExactly(
                        "s",
                        "s/setupRequest",
                        "s/setupSuite",
                        "s/mainRequest",
                        "s/mainSuite",
                        "s/teardownSuite",
                        "s/teardownRequest");
    }

    @Test
    void walk_keepsDeclarationOrderWithinEachStepTeardownIncluded() {
        // given - a teardown list is the author's stated order, and is not reversed
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(
                        request("m1", Phase.MAIN),
                        request("t1", Phase.TEARDOWN),
                        request("m2", Phase.MAIN),
                        request("t2", Phase.TEARDOWN)),
                List.of(
                        suite("tearA", Phase.TEARDOWN, List.of(), List.of()),
                        suite("tearB", Phase.TEARDOWN, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder()).containsExactly("s", "s/m1", "s/m2", "s/tearA", "s/tearB", "s/t1", "s/t2");
    }

    @Test
    void walk_appliesTheSameStepsInsideASubSuite() {
        // given - a setup block with its own setup and teardown; no nesting special case
        var block = suite(
                "login",
                Phase.SETUP,
                List.of(request("logout", Phase.TEARDOWN), request("token", Phase.MAIN), request("csrf", Phase.SETUP)),
                List.of());
        var suite = suite("s", Phase.MAIN, List.of(request("ping", Phase.MAIN)), List.of(block));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder())
                .containsExactly("s", "s/login", "s/login/csrf", "s/login/token", "s/login/logout", "s/ping");
    }

    @Test
    void walk_exitsAChildBeforeTheNextSiblingIsEntered() {
        // given
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(),
                List.of(
                        suite("a", Phase.MAIN, List.of(request("r", Phase.MAIN)), List.of()),
                        suite("b", Phase.MAIN, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace())
                .containsExactly(
                        "enter s",
                        "enter s/a",
                        "start s/a/r",
                        "finish s/a/r",
                        "exit s/a Completed",
                        "enter s/b",
                        "exit s/b Completed",
                        "exit s Completed");
    }

    @Test
    void walk_walksARootDeclaringAPhaseExactlyLikeAMainRoot() {
        // given - the root is nobody's child, so its marker places it nowhere
        for (var phase : List.of(Phase.SETUP, Phase.TEARDOWN)) {
            events.clear();
            var suite = suite("s", phase, List.of(request("r", Phase.MAIN)), List.of());

            // when
            underTest.walk(suite, runtimeData);

            // then
            assertThat(trace())
                    .as("root phase %s", phase)
                    .containsExactly("enter s", "start s/r", "finish s/r", "exit s Completed");
        }
    }

    @Test
    void walk_entersAndExitsASuiteWithNothingInIt() {
        // when
        underTest.walk(suite("s", Phase.MAIN, List.of(), List.of()), runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "exit s Completed");
    }

    // ---------- addressing ----------

    @Test
    void walk_addressesEveryNodeByTheNamesFromTheRootDown() {
        // given
        var suite = suite(
                "root",
                Phase.MAIN,
                List.of(),
                List.of(suite("child", Phase.MAIN, List.of(request("req", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(events)
                .filteredOn(RunEvent.SuiteEntered.class::isInstance)
                .map(RunEvent.SuiteEntered.class::cast)
                .extracting(RunEvent.SuiteEntered::path, RunEvent.SuiteEntered::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("root", "root"),
                        org.assertj.core.groups.Tuple.tuple("root/child", "child"));
        assertThat(results)
                .singleElement()
                .extracting(RequestResult::coordinates)
                .extracting(RequestCoordinates::path, RequestCoordinates::name)
                .containsExactly("root/child/req", "req");
    }

    @Test
    void walk_numbersRequestsFromOneAcrossTheWholeWalk() {
        // given
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(request("a", Phase.SETUP), request("c", Phase.MAIN)),
                List.of(suite("sub", Phase.SETUP, List.of(request("b", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - the setup subSuite's request runs between a and c
        assertThat(results)
                .extracting(result -> result.coordinates().requestNo())
                .containsExactly(1, 2, 3);
        assertThat(results).extracting(result -> result.coordinates().name()).containsExactly("a", "b", "c");
    }

    @Test
    void walk_numbersFromOneAgainOnEveryWalk() {
        // given
        var suite = suite("s", Phase.MAIN, List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());
        underTest.walk(suite, runtimeData);
        results.clear();

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(results)
                .extracting(result -> result.coordinates().requestNo())
                .containsExactly(1, 2);
    }

    // ---------- requests ----------

    @Test
    void walk_handsEveryResultOnInExecutionOrder() {
        // given
        var suite = suite("s", Phase.MAIN, List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then - the RequestFinished event and the consumer see the same instance
        assertThat(results).extracting(result -> result.coordinates().name()).containsExactly("a", "b");
        assertThat(events)
                .filteredOn(RunEvent.RequestFinished.class::isInstance)
                .map(event -> ((RunEvent.RequestFinished) event).result())
                .containsExactlyElementsOf(results);
    }

    @Test
    void walk_processesARequestWithOptionsBuiltFromItsOwnTimeout() {
        // given
        var request = new Request("r", null, null, "750", null, null, null, definition(), null, null);
        var suite = suite("s", Phase.MAIN, List.of(request), List.of());
        var options = ArgumentCaptor.forClass(RequestOptions.class);

        // when
        underTest.walk(suite, runtimeData);

        // then
        verify(processor).process(eq(request), options.capture(), any(), eq(runtimeData), eq(handler));
        assertThat(options.getValue().getTimeout()).isEqualTo("750");
        assertThat(options.getValue().isInterpolated()).isFalse();
    }

    @Test
    void walk_selectsTheHandlerByTheRequestsNamesMergedOverItsSuites() {
        // given
        var request = new Request("r", null, null, null, null, null, Map.of("http", "mtls"), definition(), null, null);
        var suite = new TestSuite(
                "s",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("http", "plain", "ftp", "passive"),
                List.of(request),
                null);

        // when
        underTest.walk(suite, runtimeData);

        // then - the request wins on http and keeps the suite's ftp
        verify(protocolRegistry).resolve(any(), eq(Map.of("http", "mtls", "ftp", "passive")));
    }

    // ---------- inheritance ----------

    @Test
    void walk_givesEveryRequestTheNearestDeclaredTimeout() {
        // given
        var child = new TestSuite(
                "child",
                null,
                null,
                null,
                null,
                "2000",
                null,
                null,
                null,
                List.of(request("inherits", Phase.MAIN), timedRequest("own", "3000")),
                null);
        var root = new TestSuite(
                "root",
                null,
                null,
                null,
                null,
                "1000",
                null,
                null,
                null,
                List.of(request("top", Phase.MAIN)),
                List.of(child));

        // when
        underTest.walk(root, runtimeData);

        // then
        assertThat(timeoutsByRequest())
                .containsExactlyInAnyOrderEntriesOf(Map.of("top", "1000", "inherits", "2000", "own", "3000"));
    }

    @Test
    void walk_passesASuitesHandlerNamesToRequestsAtAnyDepth() {
        // given
        var grandchild = suite("grandchild", Phase.MAIN, List.of(request("r", Phase.MAIN)), List.of());
        var child = suite("child", Phase.MAIN, List.of(), List.of(grandchild));
        var root = new TestSuite(
                "root", null, null, null, null, null, null, null, Map.of("http", "plain"), null, List.of(child));

        // when
        underTest.walk(root, runtimeData);

        // then
        verify(protocolRegistry).resolve(any(), eq(Map.of("http", "plain")));
    }

    @Test
    void walk_letsANearerSuiteOverrideOneProtocolAndKeepTheRest() {
        // given
        var child = new TestSuite(
                "child",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("http", "mtls"),
                List.of(request("r", Phase.MAIN)),
                null);
        var root = new TestSuite(
                "root",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of("http", "plain", "ftp", "passive"),
                null,
                List.of(child));

        // when
        underTest.walk(root, runtimeData);

        // then
        verify(protocolRegistry).resolve(any(), eq(Map.of("http", "mtls", "ftp", "passive")));
    }

    @Test
    void walk_keepsASubSuitesDeclarationsFromItsSiblingsAndItsParent() {
        // given - "declaring" sets both fields; its sibling and the parent's teardown run after it
        var declaring = new TestSuite(
                "declaring",
                null,
                null,
                null,
                null,
                "111",
                null,
                null,
                Map.of("http", "mtls"),
                List.of(request("inside", Phase.MAIN)),
                null);
        var sibling = suite("sibling", Phase.MAIN, List.of(request("next", Phase.MAIN)), List.of());
        var root = new TestSuite(
                "root",
                null,
                null,
                null,
                null,
                "1000",
                null,
                null,
                null,
                List.of(request("cleanUp", Phase.TEARDOWN)),
                List.of(declaring, sibling));
        var names = ArgumentCaptor.forClass(Map.class);

        // when
        underTest.walk(root, runtimeData);

        // then
        assertThat(timeoutsByRequest())
                .containsExactlyInAnyOrderEntriesOf(Map.of("inside", "111", "next", "1000", "cleanUp", "1000"));
        verify(protocolRegistry, times(3)).resolve(any(), names.capture());
        assertThat(names.getAllValues()).containsExactly(Map.of("http", "mtls"), Map.of(), Map.of());
    }

    // ---------- elapsed time ----------

    @Test
    void walk_reportsTheTimeFromEnteringASuiteToLeavingIt() {
        // given - a request that takes a noticeable time
        doAnswer(call -> {
                    Thread.sleep(30);
                    return new RequestResult(
                            call.getArgument(2),
                            definition(),
                            call.getArgument(1),
                            new RequestStatus.Completed(Map.of(), 1, 1),
                            30,
                            null);
                })
                .when(processor)
                .process(any(), any(), any(), any(), any());
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(),
                List.of(suite("child", Phase.MAIN, List.of(request("r", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - both the suite and its parent cover the request beneath them
        assertThat(exits())
                .extracting(RunEvent.SuiteExited::elapsedMs)
                .allSatisfy(ms -> assertThat(ms).isGreaterThanOrEqualTo(30L));
    }

    // ---------- cancellation ----------

    @Test
    void walk_emitsNothingWhenStartedOnACancelledRun() {
        // given
        runControl.cancel();

        // when
        underTest.walk(suite("s", Phase.MAIN, List.of(request("r", Phase.MAIN)), List.of()), runtimeData);

        // then
        assertThat(events).isEmpty();
        assertThat(results).isEmpty();
    }

    @Test
    void walk_letsARunningRequestFinishAndStartsNothingAfterIt() {
        // given - cancelled while the first request is being announced, i.e. while it runs
        cancelOn = event -> event instanceof RunEvent.RequestStarted started
                && started.coordinates().name().equals("a");
        var suite = suite("s", Phase.MAIN, List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/a", "finish s/a", "exit s Cancelled");
    }

    @Test
    void walk_startsNothingAfterACancelDuringASetupRequest() {
        // given - a stop pressed during a login request must not go on into the setup block or the suite
        cancelOn = event -> event instanceof RunEvent.RequestStarted started
                && started.coordinates().name().equals("login");
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(request("login", Phase.SETUP), request("csrf", Phase.SETUP), request("work", Phase.MAIN)),
                List.of(suite("seed", Phase.SETUP, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/login", "finish s/login", "exit s Cancelled");
    }

    @Test
    void walk_startsNothingAfterACancelDuringASetupSubSuite() {
        // given
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var setup = suite("seed", Phase.SETUP, List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(request("work", Phase.MAIN)),
                List.of(setup, suite("second", Phase.SETUP, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - neither the rest of the block, the next setup block nor the main phase starts
        assertThat(trace())
                .containsExactly(
                        "enter s",
                        "enter s/seed",
                        "start s/seed/a",
                        "finish s/seed/a",
                        "exit s/seed Cancelled",
                        "exit s Cancelled");
    }

    @Test
    void walk_checksCancellationBeforeEnteringASuite() {
        // given - a listener cancelling on a request's result; the next node is a suite
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(request("r", Phase.MAIN)),
                List.of(suite("next", Phase.MAIN, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - "next" is never entered
        assertThat(trace()).containsExactly("enter s", "start s/r", "finish s/r", "exit s Cancelled");
    }

    @Test
    void walk_exitsEveryOpenSuiteCancelledInnermostFirst() {
        // given
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var inner = suite("inner", Phase.MAIN, List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());
        var suite = suite("s", Phase.MAIN, List.of(), List.of(suite("mid", Phase.MAIN, List.of(), List.of(inner))));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(exits())
                .extracting(RunEvent.SuiteExited::path, exit -> exit.status().getClass())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("s/mid/inner", SuiteStatus.Cancelled.class),
                        org.assertj.core.groups.Tuple.tuple("s/mid", SuiteStatus.Cancelled.class),
                        org.assertj.core.groups.Tuple.tuple("s", SuiteStatus.Cancelled.class));
    }

    @Test
    void walk_runsNoTeardownAfterACancellation() {
        // given
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(request("work", Phase.MAIN), request("cleanUp", Phase.TEARDOWN)),
                List.of(suite("tearDown", Phase.TEARDOWN, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder()).containsExactly("s", "s/work");
    }

    @Test
    void walk_checksCancellationBeforeEachTeardownRequest() {
        // given - a stop pressed during teardown ends it there rather than finishing the list
        cancelOn = event -> event instanceof RunEvent.RequestStarted started
                && started.coordinates().name().equals("t1");
        var suite = suite(
                "s", Phase.MAIN, List.of(request("t1", Phase.TEARDOWN), request("t2", Phase.TEARDOWN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/t1", "finish s/t1", "exit s Cancelled");
    }

    @Test
    void walk_exitsASuiteCompletedWhenItsLastChildFinishedBeforeTheCancelWasObserved() {
        // given - cancelled as the child's last request finishes; the child has nothing left to start
        cancelOn = event -> event instanceof RunEvent.RequestFinished finished
                && finished.result().coordinates().name().equals("last");
        var suite = suite(
                "s",
                Phase.MAIN,
                List.of(),
                List.of(
                        suite("child", Phase.MAIN, List.of(request("last", Phase.MAIN)), List.of()),
                        suite("sibling", Phase.MAIN, List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace())
                .containsExactly(
                        "enter s",
                        "enter s/child",
                        "start s/child/last",
                        "finish s/child/last",
                        "exit s/child Completed",
                        "exit s Cancelled");
    }

    // ---------- structural failure ----------

    @Test
    void walk_throwsWithoutAnnouncingARequestWhoseHandlerCannotBeResolved() {
        // given
        when(protocolRegistry.resolve(any(), any())).thenThrow(new BratException("no handler named 'mtls'"));
        var suite = suite("s", Phase.MAIN, List.of(request("r", Phase.MAIN)), List.of());

        // when / then
        assertThatThrownBy(() -> underTest.walk(suite, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mtls");
        assertThat(trace()).containsExactly("enter s");
    }

    // ---------- helpers ----------

    private static HttpRequestDefinition definition() {
        return new HttpRequestDefinition("http://localhost/x", "GET", null, null);
    }

    private static Request request(String name, Phase phase) {
        return new Request(name, null, null, null, null, phase, null, definition(), null, null);
    }

    private static TestSuite suite(String name, Phase phase, List<Request> requests, List<TestSuite> subSuites) {
        return new TestSuite(name, null, null, null, null, null, null, phase, null, requests, subSuites);
    }

    private static Request timedRequest(String name, String timeout) {
        return new Request(name, null, null, timeout, null, null, null, definition(), null, null);
    }

    /** Each request's name mapped to the timeout it was processed with. */
    private Map<String, String> timeoutsByRequest() {
        var timeouts = new java.util.LinkedHashMap<String, String>();
        for (var result : results) {
            timeouts.put(result.coordinates().name(), result.requestOptions().getTimeout());
        }
        return timeouts;
    }

    /** The events as short lines, so an order is readable in a failure message. */
    private List<String> trace() {
        return events.stream()
                .map(event -> switch (event) {
                    case RunEvent.SuiteEntered entered -> "enter " + entered.path();
                    case RunEvent.SuiteExited exited ->
                        "exit " + exited.path() + " "
                                + exited.status().getClass().getSimpleName();
                    case RunEvent.RequestStarted started ->
                        "start " + started.coordinates().path();
                    case RunEvent.RequestFinished finished ->
                        "finish " + finished.result().coordinates().path();
                    default -> event.getClass().getSimpleName();
                })
                .toList();
    }

    /** Every node's path, in the order it was entered or started. */
    private List<String> nodeOrder() {
        return events.stream()
                .map(event -> switch (event) {
                    case RunEvent.SuiteEntered entered -> entered.path();
                    case RunEvent.RequestStarted started ->
                        started.coordinates().path();
                    default -> null;
                })
                .filter(path -> path != null)
                .toList();
    }

    private List<RunEvent.SuiteExited> exits() {
        return events.stream()
                .filter(RunEvent.SuiteExited.class::isInstance)
                .map(RunEvent.SuiteExited.class::cast)
                .toList();
    }

    /** A run control a test can flip, as a listener or the caller would. */
    private static final class FlagRunControl implements RunControl {

        private boolean cancelled;

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
