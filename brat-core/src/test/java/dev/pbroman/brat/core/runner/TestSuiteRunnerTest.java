package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import dev.pbroman.brat.core.data.result.SuiteError;
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
    private final List<SuiteError> errors = new ArrayList<>();
    /** Events and errors together, in the order the walk handed them on. */
    private final List<Object> journal = new ArrayList<>();

    private final FlagRunControl runControl = new FlagRunControl();
    /** What the entry evaluator answers, by suite name; a suite not in it is walked into. */
    private final Map<String, SuiteStatus> entryVerdicts = new java.util.HashMap<>();

    private RequestProcessor processor;
    private ProtocolRegistry protocolRegistry;
    private SuiteEntryEvaluator entryEvaluator;
    private RequestHandler<RequestDefinition, Object> handler;

    /** The waitAfterMs each request's result reports, by request name; absent means none. */
    private final Map<String, Long> pauses = new java.util.HashMap<>();

    /** The names of the requests whose result fails. */
    private Predicate<String> failing = name -> false;

    /** Cancels the run when an event matching it is emitted, the way a listener would. */
    private Predicate<RunEvent> cancelOn = event -> false;

    private TestSuiteRunner underTest;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        processor = mock(RequestProcessor.class);
        protocolRegistry = mock(ProtocolRegistry.class);
        entryEvaluator = mock(SuiteEntryEvaluator.class);
        handler = mock(RequestHandler.class);
        when(protocolRegistry.resolve(any(), any())).thenReturn(handler);
        when(entryEvaluator.enter(any(), any()))
                .thenAnswer(call -> Optional.ofNullable(entryVerdicts.get(((TestSuite) call.getArgument(0)).name())));
        when(processor.process(any(), any(), any(), any(), any())).thenAnswer(call -> {
            var request = (Request) call.getArgument(0);
            RequestStatus status = failing.test(request.name())
                    ? new RequestStatus.Errored("boom")
                    : new RequestStatus.Completed(Map.of(), 1, 1);
            return new RequestResult(
                    call.getArgument(2),
                    request.requestDefinition(),
                    call.getArgument(1),
                    status,
                    1,
                    pauses.getOrDefault(request.name(), 0L),
                    null);
        });
        underTest = new TestSuiteRunner(
                processor,
                protocolRegistry,
                entryEvaluator,
                event -> {
                    events.add(event);
                    journal.add(event);
                    if (cancelOn.test(event)) {
                        runControl.cancel();
                    }
                },
                results::add,
                error -> {
                    errors.add(error);
                    journal.add(error);
                },
                runControl);
    }

    // ---------- order ----------

    @Test
    void walk_bracketsASuiteWithItsEnteredAndExitedEvents() {
        // given
        var suite = suite("s", List.of(request("r", Phase.MAIN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/r", "finish s/r", "exit s Completed");
    }

    @Test
    void walk_runsTheFourStepsInOrderWhateverTheDeclarationOrder() {
        // given - every request category declared in the opposite order to the one it runs in
        var suite = suite(
                "s",
                List.of(
                        request("teardownRequest", Phase.TEARDOWN),
                        request("mainRequest", Phase.MAIN),
                        request("setupRequest", Phase.SETUP)),
                List.of(suite("first", List.of(), List.of()), suite("second", List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder())
                .containsExactly("s", "s/setupRequest", "s/mainRequest", "s/first", "s/second", "s/teardownRequest");
    }

    @Test
    void walk_keepsDeclarationOrderWithinEachStepTeardownIncluded() {
        // given - a teardown list is the author's stated order, and is not reversed
        var suite = suite(
                "s",
                List.of(
                        request("m1", Phase.MAIN),
                        request("t1", Phase.TEARDOWN),
                        request("m2", Phase.MAIN),
                        request("t2", Phase.TEARDOWN)),
                List.of(suite("a", List.of(), List.of()), suite("b", List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder()).containsExactly("s", "s/m1", "s/m2", "s/a", "s/b", "s/t1", "s/t2");
    }

    @Test
    void walk_appliesTheSameStepsInsideASubSuite() {
        // given - a subSuite with its own setup and teardown requests; no nesting special case
        var inner = suite(
                "login",
                List.of(request("logout", Phase.TEARDOWN), request("token", Phase.MAIN), request("csrf", Phase.SETUP)),
                List.of());
        var suite = suite("s", List.of(request("ping", Phase.MAIN)), List.of(inner));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder())
                .containsExactly("s", "s/ping", "s/login", "s/login/csrf", "s/login/token", "s/login/logout");
    }

    @Test
    void walk_exitsAChildBeforeTheNextSiblingIsEntered() {
        // given
        var suite = suite(
                "s",
                List.of(),
                List.of(suite("a", List.of(request("r", Phase.MAIN)), List.of()), suite("b", List.of(), List.of())));

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
    void walk_entersAndExitsASuiteWithNothingInIt() {
        // when
        underTest.walk(suite("s", List.of(), List.of()), runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "exit s Completed");
    }

    // ---------- addressing ----------

    @Test
    void walk_addressesEveryNodeByTheNamesFromTheRootDown() {
        // given
        var suite = suite("root", List.of(), List.of(suite("child", List.of(request("req", Phase.MAIN)), List.of())));

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
                List.of(request("a", Phase.MAIN), request("c", Phase.MAIN), request("z", Phase.SETUP)),
                List.of(suite("sub", List.of(request("b", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - numbered in the order they ran, not the order they were declared
        assertThat(results).extracting(result -> result.coordinates().name()).containsExactly("z", "a", "c", "b");
        assertThat(results)
                .extracting(result -> result.coordinates().requestNo())
                .containsExactly(1, 2, 3, 4);
    }

    @Test
    void walk_numbersFromOneAgainOnEveryWalk() {
        // given
        var suite = suite("s", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());
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
    void walk_carriesEachRequestsPhaseOnItsStartEventAndItsResult() {
        // given
        var suite = suite(
                "s",
                List.of(request("logout", Phase.TEARDOWN), request("work", Phase.MAIN), request("login", Phase.SETUP)),
                List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then - the phase is known before any result exists, so an IDE can mark a setup node as it starts
        assertThat(events)
                .filteredOn(RunEvent.RequestStarted.class::isInstance)
                .extracting(
                        event -> ((RunEvent.RequestStarted) event).coordinates().phase())
                .containsExactly(Phase.SETUP, Phase.MAIN, Phase.TEARDOWN);
        assertThat(results)
                .extracting(result -> result.coordinates().phase())
                .containsExactly(Phase.SETUP, Phase.MAIN, Phase.TEARDOWN);
    }

    @Test
    void walk_handsEveryResultOnInExecutionOrder() {
        // given
        var suite = suite("s", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());

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
        var suite = suite("s", List.of(request), List.of());
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
        var grandchild = suite("grandchild", List.of(request("r", Phase.MAIN)), List.of());
        var child = suite("child", List.of(), List.of(grandchild));
        var root = new TestSuite(
                "root", null, null, null, null, null, null, Map.of("http", "plain"), null, List.of(child));

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
                Map.of("http", "mtls"),
                List.of(request("inside", Phase.MAIN)),
                null);
        var sibling = suite("sibling", List.of(request("next", Phase.MAIN)), List.of());
        var root = new TestSuite(
                "root",
                null,
                null,
                null,
                null,
                "1000",
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

    // ---------- entry: current path, skip, abort ----------

    @Test
    void walk_setsTheCurrentPathToTheSuiteBeforeEvaluatingItsEntry() {
        // given - a tombstone left by a failed suite setVars must name that suite, not the last request
        var seen = new ArrayList<String>();
        doAnswer(call -> {
                    seen.add(((RuntimeData) call.getArgument(1)).getCurrentPath());
                    return Optional.empty();
                })
                .when(entryEvaluator)
                .enter(any(), any());
        var suite = suite("s", List.of(request("r", Phase.MAIN)), List.of(suite("child", List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - "child" is entered after request "r" set the path to itself
        assertThat(seen).containsExactly("s", "s/child");
    }

    @Test
    void walk_evaluatesTheEntryAfterSuiteEntered() {
        // given
        var eventsAtEntry = new ArrayList<Integer>();
        doAnswer(call -> {
                    eventsAtEntry.add(events.size());
                    return Optional.empty();
                })
                .when(entryEvaluator)
                .enter(any(), any());

        // when
        underTest.walk(suite("s", List.of(), List.of()), runtimeData);

        // then - SuiteEntered had already gone out
        assertThat(eventsAtEntry).containsExactly(1);
    }

    @Test
    void walk_exitsASkippedSuiteWalkingNothingBeneathIt() {
        // given
        entryVerdicts.put("skipped", new SuiteStatus.Skipped("Skipped due to condition true isTrue"));
        var skipped = suite(
                "skipped",
                List.of(request("r", Phase.MAIN), request("cleanUp", Phase.TEARDOWN)),
                List.of(suite("deeper", List.of(), List.of())));
        var suite = suite("s", List.of(), List.of(skipped));

        // when
        underTest.walk(suite, runtimeData);

        // then - both events for the skipped node, nothing beneath it, and it is not an error
        assertThat(trace()).containsExactly("enter s", "enter s/skipped", "exit s/skipped Skipped", "exit s Completed");
        assertThat(errors).isEmpty();
    }

    @Test
    void walk_abortsAtEntryWithNoTeardownAndReportsTheError() {
        // given
        entryVerdicts.put("admin", new SuiteStatus.Aborted("setVars failed for 'token'"));
        var admin =
                suite("admin", List.of(request("login", Phase.SETUP), request("logout", Phase.TEARDOWN)), List.of());
        var suite = suite("s", List.of(), List.of(admin));

        // when
        underTest.walk(suite, runtimeData);

        // then - nothing under it acted, so nothing is undone; the error comes before the exit
        assertThat(trace())
                .containsExactly(
                        "enter s", "enter s/admin", "error s/admin", "exit s/admin Aborted", "exit s Completed");
        assertThat(errors).containsExactly(new SuiteError("s/admin", "setVars failed for 'token'"));
    }

    @Test
    void walk_carriesTheSameReasonOnTheErrorAndTheExitEvent() {
        // given
        entryVerdicts.put("admin", new SuiteStatus.Aborted("setVars failed for 'token'"));

        // when
        underTest.walk(suite("s", List.of(), List.of(suite("admin", List.of(), List.of()))), runtimeData);

        // then
        assertThat(exits())
                .filteredOn(exit -> exit.path().equals("s/admin"))
                .singleElement()
                .extracting(RunEvent.SuiteExited::status)
                .isEqualTo(new SuiteStatus.Aborted(errors.getFirst().message()));
    }

    @Test
    void walk_canSkipOrAbortTheRootItself() {
        // given
        entryVerdicts.put("s", new SuiteStatus.Skipped("Skipped due to condition true isTrue"));

        // when
        underTest.walk(suite("s", List.of(request("r", Phase.MAIN)), List.of()), runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "exit s Skipped");
    }

    // ---------- a failed setup aborts the suite declaring it ----------

    @Test
    void walk_aFailedSetupRequestAbortsTheSuiteButRunsItsTeardown() {
        // given
        failing = name -> name.equals("login");
        var suite = suite(
                "s",
                List.of(
                        request("login", Phase.SETUP),
                        request("csrf", Phase.SETUP),
                        request("work", Phase.MAIN),
                        request("logout", Phase.TEARDOWN)),
                List.of(suite("tests", List.of(request("inside", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - the rest of setup, the main requests and the subSuites are skipped; teardown unwinds
        assertThat(nodeOrder()).containsExactly("s", "s/login", "s/logout");
        assertThat(exits())
                .singleElement()
                .extracting(RunEvent.SuiteExited::status)
                .isInstanceOf(SuiteStatus.Aborted.class);
    }

    @Test
    void walk_anAbortedSubSuiteNeverAbortsItsParent() {
        // given - a subSuite whose own login fails; its sibling and the parent's teardown are unaffected
        failing = name -> name.equals("login");
        var suite = suite(
                "s",
                List.of(request("cleanUp", Phase.TEARDOWN)),
                List.of(
                        suite("admin", List.of(request("login", Phase.SETUP), request("work", Phase.MAIN)), List.of()),
                        suite("reader", List.of(request("read", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder())
                .containsExactly("s", "s/admin", "s/admin/login", "s/reader", "s/reader/read", "s/cleanUp");
        assertThat(errors).extracting(SuiteError::path).containsExactly("s/admin");
        assertThat(trace()).endsWith("exit s Completed");
    }

    @Test
    void walk_namesTheFailedSetupNodeInTheReason() {
        // given
        failing = name -> name.equals("login");

        // when
        underTest.walk(suite("s", List.of(request("login", Phase.SETUP)), List.of()), runtimeData);

        // then
        assertThat(errors).singleElement().satisfies(error -> {
            assertThat(error.path()).isEqualTo("s");
            assertThat(error.message()).contains("login");
        });
        assertThat(trace()).endsWith("error s", "exit s Aborted");
    }

    @Test
    void walk_failuresOutsideSetupAbortNothing() {
        // given - a failing main request, and a main subSuite that aborts at entry
        failing = name -> name.equals("work");
        entryVerdicts.put("broken", new SuiteStatus.Aborted("setVars failed for 'x'"));
        var suite = suite(
                "s",
                List.of(request("work", Phase.MAIN), request("more", Phase.MAIN)),
                List.of(
                        suite("broken", List.of(), List.of()),
                        suite("sibling", List.of(request("next", Phase.MAIN)), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - siblings are independent, and the parent completes
        assertThat(nodeOrder()).contains("s/more", "s/sibling/next");
        assertThat(errors).extracting(SuiteError::path).containsExactly("s/broken");
        assertThat(trace()).endsWith("exit s Completed");
    }

    @Test
    void walk_staysAbortedWhenACancellationCutsTheTeardownShort() {
        // given
        failing = name -> name.equals("login");
        cancelOn = event -> event instanceof RunEvent.RequestStarted started
                && started.coordinates().name().equals("t1");
        var suite = suite(
                "s",
                List.of(request("login", Phase.SETUP), request("t1", Phase.TEARDOWN), request("t2", Phase.TEARDOWN)),
                List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(nodeOrder()).containsExactly("s", "s/login", "s/t1");
        assertThat(trace()).endsWith("exit s Aborted");
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
                            0,
                            null);
                })
                .when(processor)
                .process(any(), any(), any(), any(), any());
        var suite = suite("s", List.of(), List.of(suite("child", List.of(request("r", Phase.MAIN)), List.of())));

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
        underTest.walk(suite("s", List.of(request("r", Phase.MAIN)), List.of()), runtimeData);

        // then
        assertThat(events).isEmpty();
        assertThat(results).isEmpty();
    }

    @Test
    void walk_letsARunningRequestFinishAndStartsNothingAfterIt() {
        // given - cancelled while the first request is being announced, i.e. while it runs
        cancelOn = event -> event instanceof RunEvent.RequestStarted started
                && started.coordinates().name().equals("a");
        var suite = suite("s", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/a", "finish s/a", "exit s Cancelled");
    }

    @Test
    void walk_startsNothingAfterACancelDuringASetupRequest() {
        // given - a stop pressed during a login request must not go on into the rest of the suite
        cancelOn = event -> event instanceof RunEvent.RequestStarted started
                && started.coordinates().name().equals("login");
        var suite = suite(
                "s",
                List.of(request("login", Phase.SETUP), request("csrf", Phase.SETUP), request("work", Phase.MAIN)),
                List.of(suite("tests", List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat(trace()).containsExactly("enter s", "start s/login", "finish s/login", "exit s Cancelled");
    }

    @Test
    void walk_checksCancellationBeforeEnteringASuite() {
        // given - a listener cancelling on a request's result; the next node is a suite
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var suite = suite("s", List.of(request("r", Phase.MAIN)), List.of(suite("next", List.of(), List.of())));

        // when
        underTest.walk(suite, runtimeData);

        // then - "next" is never entered
        assertThat(trace()).containsExactly("enter s", "start s/r", "finish s/r", "exit s Cancelled");
    }

    @Test
    void walk_exitsEveryOpenSuiteCancelledInnermostFirst() {
        // given
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var inner = suite("inner", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());
        var suite = suite("s", List.of(), List.of(suite("mid", List.of(), List.of(inner))));

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
                List.of(request("work", Phase.MAIN), request("cleanUp", Phase.TEARDOWN)),
                List.of(suite("later", List.of(), List.of())));

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
        var suite = suite("s", List.of(request("t1", Phase.TEARDOWN), request("t2", Phase.TEARDOWN)), List.of());

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
                List.of(),
                List.of(
                        suite("child", List.of(request("last", Phase.MAIN)), List.of()),
                        suite("sibling", List.of(), List.of())));

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

    // ---------- pacing ----------

    @Test
    void walk_pausesAfterARequestBeforeStartingTheNext() {
        // given
        pauses.put("a", 200L);
        var startedAt = new java.util.HashMap<String, Long>();
        cancelOn = event -> {
            if (event instanceof RunEvent.RequestStarted started) {
                startedAt.put(started.coordinates().name(), System.nanoTime());
            }
            return false;
        };
        var suite = suite("s", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of());

        // when
        underTest.walk(suite, runtimeData);

        // then
        assertThat((startedAt.get("b") - startedAt.get("a")) / 1_000_000).isGreaterThanOrEqualTo(200L);
    }

    @Test
    void walk_pausesAfterASuitesLastRequestBeforeLeavingIt() {
        // given - waitAfter means "after this request", whatever follows it
        pauses.put("last", 200L);

        // when
        underTest.walk(suite("s", List.of(request("last", Phase.MAIN)), List.of()), runtimeData);

        // then - the pause is part of the suite's elapsed time
        assertThat(exits())
                .singleElement()
                .extracting(RunEvent.SuiteExited::elapsedMs)
                .satisfies(ms -> assertThat(ms).isGreaterThanOrEqualTo(200L));
    }

    @Test
    void walk_doesNotBeginAPauseWhenTheRunIsAlreadyCancelled() {
        // given - cancelled as the request finishes, before its long pause would start
        pauses.put("a", 10_000L);
        cancelOn = event -> event instanceof RunEvent.RequestFinished;
        var started = System.nanoTime();

        // when
        underTest.walk(suite("s", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of()), runtimeData);

        // then
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000L);
        assertThat(nodeOrder()).containsExactly("s", "s/a");
    }

    @Test
    void walk_endsAPauseEarlyWhenTheRunIsCancelledDuringIt() throws InterruptedException {
        // given - a stop pressed ten seconds before the pause would end
        pauses.put("a", 10_000L);
        var canceller = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            runControl.cancel();
        });
        var started = System.nanoTime();
        canceller.start();

        // when
        underTest.walk(suite("s", List.of(request("a", Phase.MAIN), request("b", Phase.MAIN)), List.of()), runtimeData);
        canceller.join();

        // then - promptly, and nothing further started
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000L);
        assertThat(nodeOrder()).containsExactly("s", "s/a");
        assertThat(trace()).endsWith("exit s Cancelled");
    }

    @Test
    void walk_throwsAndRestoresTheFlagWhenInterruptedWhilePausing() {
        // given
        pauses.put("a", 5_000L);
        Thread.currentThread().interrupt();

        // when / then
        try {
            assertThatThrownBy(() ->
                            underTest.walk(suite("s", List.of(request("a", Phase.MAIN)), List.of()), runtimeData))
                    .isInstanceOf(BratException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    // ---------- structural failure ----------

    @Test
    void walk_throwsWithoutAnnouncingARequestWhoseHandlerCannotBeResolved() {
        // given
        when(protocolRegistry.resolve(any(), any())).thenThrow(new BratException("no handler named 'mtls'"));
        var suite = suite("s", List.of(request("r", Phase.MAIN)), List.of());

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

    private static TestSuite suite(String name, List<Request> requests, List<TestSuite> subSuites) {
        return new TestSuite(name, null, null, null, null, null, null, null, requests, subSuites);
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
        return journal.stream()
                .map(event -> switch (event) {
                    case RunEvent.SuiteEntered entered -> "enter " + entered.path();
                    case RunEvent.SuiteExited exited ->
                        "exit " + exited.path() + " "
                                + exited.status().getClass().getSimpleName();
                    case RunEvent.RequestStarted started ->
                        "start " + started.coordinates().path();
                    case RunEvent.RequestFinished finished ->
                        "finish " + finished.result().coordinates().path();
                    case SuiteError error -> "error " + error.path();
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

        private volatile boolean cancelled;

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
