package dev.pbroman.brat.core.runner;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.RequestOptions;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.data.Phase.MAIN;
import static dev.pbroman.brat.core.data.Phase.SETUP;
import static dev.pbroman.brat.core.data.Phase.TEARDOWN;
import static dev.pbroman.brat.core.util.Constants.PATH_DELIMITER;

/**
 * Walks a suite tree: visits every suite and request in the decided order, runs each request through
 * the {@link RequestProcessor}, and reports each node as it is entered and left.
 * <p>
 * Constructed once per run, over that run's collaborators. It owns order and the event pairs;
 * {@link RequestProcessor} owns everything that happens to one request, and learns nothing about the
 * tree.
 * <p>
 * <strong>Results are handed on as they are produced</strong>, one per request that ran, to the
 * consumer given at construction. The walk keeps no list of its own and never reads back a result it
 * handed on: whatever it reports about a suite comes from what it observed while walking that suite.
 */
@Slf4j
final class TestSuiteRunner {

    private final RequestProcessor processor;
    private final ProtocolRegistry protocolRegistry;
    private final Consumer<RunEvent> eventConsumer;
    private final Consumer<RequestResult> resultConsumer;
    private final RunControl runControl;

    /**
     * Constructs a runner for one run.
     *
     * @param processor runs one request and returns its result
     * @param protocolRegistry selects the handler that performs a request
     * @param eventConsumer receives every suite and request event, in the order they happen
     * @param resultConsumer receives one result per request that ran, in execution order
     * @param runControl the cancellation channel, checked before every node
     */
    TestSuiteRunner(
            RequestProcessor processor,
            ProtocolRegistry protocolRegistry,
            Consumer<RunEvent> eventConsumer,
            Consumer<RequestResult> resultConsumer,
            RunControl runControl) {
        this.processor = processor;
        this.protocolRegistry = protocolRegistry;
        this.eventConsumer = eventConsumer;
        this.resultConsumer = resultConsumer;
        this.runControl = runControl;
    }

    /**
     * Walks {@code suite} and everything beneath it.
     * <p>
     * <strong>At every suite</strong>, {@code SuiteEntered} is emitted first, then its children run in
     * five steps, then {@code SuiteExited}:
     * <ol>
     *   <li>its {@code phase: setup} requests, in declaration order;</li>
     *   <li>its {@code phase: setup} subSuites, in declaration order, each walked the same way;</li>
     *   <li>its {@code MAIN} requests, in declaration order;</li>
     *   <li>its {@code MAIN} subSuites, in declaration order, each walked the same way;</li>
     *   <li>its {@code phase: teardown} subSuites, then its {@code phase: teardown} requests — each list
     *       in declaration order.</li>
     * </ol>
     * A child's {@code phase} places it within its parent's steps. The root suite has no parent, so its
     * own {@code phase} places it nowhere: a root declaring anything but {@code MAIN} is walked exactly
     * like one declaring {@code MAIN}, and a WARN says the marker has no effect.
     * <p>
     * <strong>At every request</strong>: its handler is resolved, then {@code RequestStarted} is
     * emitted, the request is processed with {@link RequestOptions} built
     * from its own {@code timeout}, the result is handed to the results consumer, and
     * {@code RequestFinished} is emitted. The handler is selected by the request's
     * {@code requestHandlers} merged per key over those of the suite that declares it, the request's
     * entries winning; a suite's entries do not reach requests in its subSuites.
     * <p>
     * <strong>Addressing.</strong> The root suite's path is its name; a subSuite's is its parent's path,
     * {@code /}, and its name; a request's is its suite's path, {@code /}, and its name. Requests are
     * numbered from {@code 1} in execution order across the whole walk.
     * <p>
     * <strong>Events.</strong> Every suite that is entered emits {@code SuiteEntered} and later
     * {@code SuiteExited} with the same path, and every event for a node beneath it falls between the
     * two. {@code SuiteExited} carries the milliseconds from entering the suite to leaving it and a
     * status: {@code Completed} when every child ran, {@code Cancelled} when the walk stopped inside
     * it.
     * <p>
     * <strong>Cancellation</strong> is checked before entering each suite and before running each
     * request — teardown steps included. A request already running finishes. Once a check finds the
     * run cancelled, nothing further starts, and every suite still open emits
     * {@code SuiteExited(Cancelled)}, innermost first. A suite whose last child finished before the
     * cancellation was observed exits {@code Completed}. A walk started on a cancelled run emits
     * nothing at all.
     *
     * @param suite the root of the tree to walk; never {@code null}
     * @param runtimeData the run's namespaces, passed to every request; never {@code null}
     * @throws BratException if a request's handler cannot be resolved. This is structural and ends the
     *         walk where it stands: the request is not announced, and the suites still open emit no
     *         {@code SuiteExited} — the run's own terminal event is what closes them
     */
    void walk(TestSuite suite, RuntimeData runtimeData) {
        if (suite.phase() != MAIN) {
            log.warn(
                    "The root suite '{}' declares phase {}, which has no effect on a root: it is walked as MAIN",
                    suite.name(),
                    suite.phase());
        }
        walkInternal(suite, runtimeData, suite.name(), new AtomicInteger());
    }

    /**
     * Walks one suite and everything beneath it, unless the run is already cancelled.
     *
     * @param suite the suite to walk
     * @param runtimeData the run's namespaces
     * @param path the suite's own path
     * @param requestNo the walk's request counter, shared by every node of this walk
     * @return {@code true} if the walk reached the end of this suite; {@code false} if a cancellation
     *         check stopped it here or beneath it — including the check before entering it, in which
     *         case nothing was emitted. {@code false} means cancellation and nothing else
     */
    private boolean walkInternal(TestSuite suite, RuntimeData runtimeData, String path, AtomicInteger requestNo) {
        if (runControl.isCancelled()) {
            return false;
        }

        var startTime = System.currentTimeMillis();
        eventConsumer.accept(new RunEvent.SuiteEntered(path, suite.name()));

        var suiteCompleted = performRequests(suite, runtimeData, SETUP, path, requestNo)
                && executeSubSuites(suite, runtimeData, SETUP, path, requestNo)
                && performRequests(suite, runtimeData, MAIN, path, requestNo)
                && executeSubSuites(suite, runtimeData, MAIN, path, requestNo)
                && executeSubSuites(suite, runtimeData, TEARDOWN, path, requestNo)
                && performRequests(suite, runtimeData, TEARDOWN, path, requestNo);

        exitSuite(startTime, path, suiteCompleted);
        return suiteCompleted;
    }

    /**
     * Emits the suite's {@code SuiteExited}.
     *
     * @param startTime when the suite was entered, in milliseconds
     * @param path the suite's path
     * @param suiteCompleted whether the walk reached the end of the suite; {@code false} exits it
     *        {@code Cancelled}
     */
    private void exitSuite(long startTime, String path, boolean suiteCompleted) {
        var status = suiteCompleted ? new SuiteStatus.Completed() : new SuiteStatus.Cancelled();
        eventConsumer.accept(new RunEvent.SuiteExited(path, status, System.currentTimeMillis() - startTime));
    }

    /**
     * Walks the subSuites of {@code suite} that declare {@code phase}, in declaration order.
     *
     * @param suite the parent suite
     * @param runtimeData the run's namespaces
     * @param phase the step being run
     * @param path the parent's path
     * @param requestNo the walk's request counter
     * @return {@code true} if every one of them was walked to its end; {@code false} as soon as one
     *         reports that a cancellation check stopped it, leaving the rest unentered
     */
    private boolean executeSubSuites(
            TestSuite suite, RuntimeData runtimeData, Phase phase, String path, AtomicInteger requestNo) {
        for (TestSuite subSuite : suite.subSuites()) {
            if (subSuite.phase() != phase) {
                continue;
            }
            if (!walkInternal(subSuite, runtimeData, childPath(path, subSuite.name()), requestNo)) {
                return false;
            }
        }
        return true;
    }

    private static String childPath(String parentPath, String name) {
        return parentPath + PATH_DELIMITER + name;
    }

    /**
     * Runs the requests of {@code suite} that declare {@code phase}, in declaration order.
     *
     * @param suite the suite declaring them
     * @param runtimeData the run's namespaces
     * @param phase the step being run
     * @param path the suite's path
     * @param requestNo the walk's request counter, advanced once per request started
     * @return {@code true} if every one of them ran; {@code false} if the cancellation check before one
     *         of them stopped the walk, leaving it and the rest unstarted
     */
    private boolean performRequests(
            TestSuite suite, RuntimeData runtimeData, Phase phase, String path, AtomicInteger requestNo) {
        for (Request request : suite.requests()) {
            if (request.phase() != phase) {
                continue;
            }
            if (runControl.isCancelled()) {
                return false;
            }
            var coordinates = new RequestCoordinates(
                    childPath(path, request.name()), request.id(), request.name(), requestNo.incrementAndGet());
            // Resolved before the request is announced: a handler this wiring lacks aborts the run,
            // and a RequestStarted with no RequestFinished would leave a listener's test tree holding
            // a node that never ends.
            var handler = protocolRegistry.resolve(request.requestDefinition(), effectiveHandlerNames(suite, request));
            eventConsumer.accept(new RunEvent.RequestStarted(coordinates));

            var options = new RequestOptions(request.timeout());
            var result = processor.process(request, options, coordinates, runtimeData, handler);
            resultConsumer.accept(result);
            eventConsumer.accept(new RunEvent.RequestFinished(result));
        }
        return true;
    }

    /**
     * The handler names in effect for {@code request}.
     * <p>
     * Merged per key rather than replaced wholesale: a request overriding {@code http} must not drop
     * an {@code ftp} entry it inherited. The request's own entries win over its suite's.
     *
     * @param suite the suite that declares the request
     * @param request the request about to run
     * @return the effective protocol-to-handler-name map; never {@code null}, possibly empty
     */
    private static Map<String, String> effectiveHandlerNames(TestSuite suite, Request request) {
        var names = new HashMap<>(suite.requestHandlers());
        names.putAll(request.requestHandlers());
        return names;
    }
}
