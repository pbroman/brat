package dev.pbroman.brat.core.runner;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import dev.pbroman.brat.core.data.result.SuiteError;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

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
 * <strong>Results and errors are handed on as they are produced</strong> — one result per request that
 * ran, one error per suite that was aborted — to the consumers given at construction. The walk keeps
 * no list of its own and never reads back anything it handed on: whatever it reports about a suite
 * comes from what it observed while walking that suite.
 */
final class TestSuiteRunner {

    /** How long the walk sleeps between cancellation checks during a pause. */
    private static final Duration PAUSE_SLICE = Duration.ofMillis(100);

    private final RequestProcessor processor;
    private final ProtocolRegistry protocolRegistry;
    private final SuiteEntryEvaluator entryEvaluator;
    private final Consumer<RunEvent> eventConsumer;
    private final Consumer<RequestResult> resultConsumer;
    private final Consumer<SuiteError> errorConsumer;
    private final RunControl runControl;

    /**
     * Constructs a runner for one run.
     *
     * @param processor runs one request and returns its result
     * @param protocolRegistry selects the handler that performs a request
     * @param entryEvaluator evaluates each suite's {@code setVars} and {@code skipCondition} on entry
     * @param eventConsumer receives every suite and request event, in the order they happen
     * @param resultConsumer receives one result per request that ran, in execution order
     * @param errorConsumer receives one error per suite that was aborted, in the order they are left
     * @param runControl the cancellation channel, checked before every node
     */
    TestSuiteRunner(
            RequestProcessor processor,
            ProtocolRegistry protocolRegistry,
            SuiteEntryEvaluator entryEvaluator,
            Consumer<RunEvent> eventConsumer,
            Consumer<RequestResult> resultConsumer,
            Consumer<SuiteError> errorConsumer,
            RunControl runControl) {
        this.processor = processor;
        this.protocolRegistry = protocolRegistry;
        this.entryEvaluator = entryEvaluator;
        this.eventConsumer = eventConsumer;
        this.resultConsumer = resultConsumer;
        this.errorConsumer = errorConsumer;
        this.runControl = runControl;
    }

    /**
     * Walks {@code suite} and everything beneath it.
     * <p>
     * <strong>At every suite</strong>, {@code SuiteEntered} is emitted first and {@code runtimeData}'s
     * current path set to the suite's path. Then its entry is evaluated — its {@code setVars}, then its
     * {@code skipCondition} (see {@link SuiteEntryEvaluator#enter}) — and, unless that skips or aborts
     * it, what it contains runs in four steps, then {@code SuiteExited}:
     * <ol>
     *   <li>its {@code phase: setup} requests;</li>
     *   <li>its other requests;</li>
     *   <li>its subSuites, each walked the same way;</li>
     *   <li>its {@code phase: teardown} requests.</li>
     * </ol>
     * Each step keeps declaration order. Only requests carry a phase; a subSuite runs in step 3.
     * <p>
     * <strong>A suite that does not run.</strong> If its entry says <em>skip</em>, it exits
     * {@code Skipped} with nothing beneath it walked. If its entry says <em>abort</em>, it exits
     * {@code Aborted} with nothing beneath it walked and <strong>no teardown</strong> — nothing under it
     * acted, so there is nothing to undo.
     * <p>
     * <strong>A failed setup request aborts the suite declaring it.</strong> A setup request fails when
     * its result {@link RequestResult#failed() failed}. The suite's remaining setup requests, its other
     * requests and its subSuites are then not run, its <strong>teardown requests still run</strong> — an
     * earlier setup request may have acted — and it exits {@code Aborted}, the reason naming the setup
     * request that failed. A failure anywhere else is the request's own and aborts nothing, and an
     * aborted suite never aborts its parent.
     * <p>
     * <strong>Every abort is reported twice, from one value</strong>: a {@link SuiteError} with the
     * suite's path and the abort reason is handed to the error consumer, and then
     * {@code SuiteExited(Aborted)} is emitted with the same reason.
     * <p>
     * <strong>At every request</strong>: its handler is resolved, then {@code RequestStarted} is
     * emitted, the request is processed with its {@link RequestOptions}, the result is handed to the
     * results consumer, and {@code RequestFinished} is emitted. Then the walk <strong>pauses</strong> for the result's
     * {@link RequestResult#waitAfterMs() waitAfterMs} before starting anything else — the next request,
     * a subSuite, or leaving the suite — so a suite's elapsed time includes its pacing. A pause is not
     * begun when the run is already cancelled, and one under way ends within a fraction of a second
     * of a cancellation; either way nothing further starts.
     * <p>
     * <strong>Inheritance.</strong> What a suite declares for its requests reaches every request
     * beneath it, at any depth, and nothing outside it — not its parent, not its siblings:
     * <ul>
     *   <li>{@code timeout}: the request's own if it declares one, otherwise the nearest enclosing
     *       suite's; with none declared anywhere, the default applies.</li>
     *   <li>{@code requestHandlers}: merged per protocol from the root down to the request, the nearer
     *       declaration winning where two name the same protocol, and a protocol only an outer suite
     *       names still reaching the request.</li>
     * </ul>
     * Only a {@code null} is "not declared": a {@code timeout} of {@code default} (or blank) is a
     * declaration, overriding what would have been inherited and reading as the default.
     * <p>
     * <strong>Addressing.</strong> The root suite's path is its name; a subSuite's is its parent's path,
     * {@code /}, and its name; a request's is its suite's path, {@code /}, and its name. Requests are
     * numbered from {@code 1} in execution order across the whole walk.
     * <p>
     * <strong>Events.</strong> Every suite that is entered emits {@code SuiteEntered} and later
     * {@code SuiteExited} with the same path, and every event for a node beneath it falls between the
     * two. {@code SuiteExited} carries the milliseconds from entering the suite to leaving it and a
     * status: {@code Completed} when every step ran — whatever the requests in them did —
     * {@code Skipped} or {@code Aborted} as above, and {@code Cancelled} when the walk stopped inside it
     * for a cancellation. A suite that is skipped, aborted or cancelled leaves its siblings unaffected;
     * only a cancellation stops them too, by stopping the walk.
     * <p>
     * <strong>Cancellation</strong> is checked before entering each suite and before running each
     * request — teardown steps included. A request already running finishes. Once a check finds the
     * run cancelled, nothing further starts, and every suite still open emits
     * {@code SuiteExited(Cancelled)}, innermost first. A suite whose last child finished before the
     * cancellation was observed exits {@code Completed}, and one already aborted stays {@code Aborted}
     * when a cancellation cuts its teardown short. A walk started on a cancelled run emits nothing at
     * all.
     *
     * @param suite the root of the tree to walk; must not be {@code null}
     * @param runtimeData the run's namespaces, passed to every request; must not be {@code null}
     * @throws BratException if a request's handler cannot be resolved. This is structural and ends the
     *         walk where it stands: the request is not announced, and the suites still open emit no
     *         {@code SuiteExited} — the run's own terminal event is what closes them
     * @throws BratException if the thread is interrupted during a pause, ending the walk the same way
     */
    void walk(TestSuite suite, RuntimeData runtimeData) {
        walkSuite(suite, runtimeData, suite.name(), InheritedDefaults.NONE, new AtomicInteger());
    }

    /**
     * Walks one suite and everything beneath it, unless the run is already cancelled.
     *
     * @param suite the suite to walk
     * @param runtimeData the run's namespaces
     * @param path the suite's own path
     * @param inherited what the enclosing suites declared, not yet including this one
     * @param requestNo the walk's request counter, shared by every node of this walk
     * @return {@code false} if a cancellation stopped the walk here or beneath it — including the check
     *         before entering it, in which case nothing was emitted — and {@code true} otherwise, however
     *         the suite itself ended
     */
    private boolean walkSuite(
            TestSuite suite,
            RuntimeData runtimeData,
            String path,
            InheritedDefaults inherited,
            AtomicInteger requestNo) {
        if (runControl.isCancelled()) {
            return false;
        }
        var startTime = System.currentTimeMillis();
        eventConsumer.accept(new RunEvent.SuiteEntered(path, suite.name()));
        runtimeData.setCurrentPath(path);

        var entry = entryEvaluator.enter(suite, runtimeData);
        if (entry.isPresent()) {
            exitSuite(path, startTime, entry.get());
            return true;
        }

        var status = runSteps(suite, runtimeData, path, inherited.with(suite), requestNo);
        exitSuite(path, startTime, status);
        return !(status instanceof SuiteStatus.Cancelled);
    }

    /**
     * Runs a suite's four steps in order, stopping at the first that does not complete.
     *
     * @param suite the suite being walked
     * @param runtimeData the run's namespaces
     * @param path the suite's path
     * @param defaults what the suite and its ancestors declared
     * @param requestNo the walk's request counter
     * @return how the suite ended: {@code Completed} if every step ran, {@code Aborted} if a setup
     *         request failed (its teardown having run), {@code Cancelled} if a cancellation stopped it
     */
    private SuiteStatus runSteps(
            TestSuite suite,
            RuntimeData runtimeData,
            String path,
            InheritedDefaults defaults,
            AtomicInteger requestNo) {
        var stopped = runRequests(suite, runtimeData, SETUP, path, defaults, requestNo);
        if (stopped.isPresent()) {
            if (stopped.get() instanceof SuiteStatus.Aborted) {
                // An earlier setup request may have acted, so the teardown still unwinds it. A
                // cancellation cutting it short does not change why the suite ended.
                runRequests(suite, runtimeData, TEARDOWN, path, defaults, requestNo);
            }
            return stopped.get();
        }
        stopped = runRequests(suite, runtimeData, MAIN, path, defaults, requestNo);
        if (stopped.isPresent()) {
            return stopped.get();
        }
        stopped = walkSubSuites(suite, runtimeData, path, defaults, requestNo);
        if (stopped.isPresent()) {
            return stopped.get();
        }
        return runRequests(suite, runtimeData, TEARDOWN, path, defaults, requestNo)
                .orElse(new SuiteStatus.Completed());
    }

    /**
     * Reports how a suite ended: an abort first as a {@link SuiteError}, then the {@code SuiteExited}.
     *
     * @param path the suite's path
     * @param startTime when the suite was entered, in milliseconds
     * @param status how it ended
     */
    private void exitSuite(String path, long startTime, SuiteStatus status) {
        if (status instanceof SuiteStatus.Aborted aborted) {
            errorConsumer.accept(new SuiteError(path, aborted.reason()));
        }
        eventConsumer.accept(new RunEvent.SuiteExited(path, status, System.currentTimeMillis() - startTime));
    }

    /**
     * Walks the subSuites of {@code suite}, in declaration order.
     *
     * @param suite the parent suite
     * @param runtimeData the run's namespaces
     * @param path the parent's path
     * @param defaults what the parent and its ancestors declared
     * @param requestNo the walk's request counter
     * @return {@link SuiteStatus.Cancelled} as soon as a cancellation stops the walk, leaving the rest
     *         unentered; empty otherwise, whatever the subSuites themselves ended as
     */
    private Optional<SuiteStatus> walkSubSuites(
            TestSuite suite,
            RuntimeData runtimeData,
            String path,
            InheritedDefaults defaults,
            AtomicInteger requestNo) {
        for (TestSuite subSuite : suite.subSuites()) {
            if (!walkSuite(subSuite, runtimeData, childPath(path, subSuite.name()), defaults, requestNo)) {
                return Optional.of(new SuiteStatus.Cancelled());
            }
        }
        return Optional.empty();
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
     * @param defaults what the suite and its ancestors declared
     * @param requestNo the walk's request counter, advanced once per request started
     * @return empty if every one of them ran; {@link SuiteStatus.Cancelled} if the cancellation check
     *         before one stopped the walk; {@link SuiteStatus.Aborted} if {@code phase} is
     *         {@code SETUP} and one of them failed. Either way the rest are left unstarted
     */
    private Optional<SuiteStatus> runRequests(
            TestSuite suite,
            RuntimeData runtimeData,
            Phase phase,
            String path,
            InheritedDefaults defaults,
            AtomicInteger requestNo) {
        for (Request request : suite.requests()) {
            if (request.phase() != phase) {
                continue;
            }
            if (runControl.isCancelled()) {
                return Optional.of(new SuiteStatus.Cancelled());
            }
            var coordinates = new RequestCoordinates(
                    childPath(path, request.name()),
                    request.id(),
                    request.name(),
                    request.phase(),
                    requestNo.incrementAndGet());
            // Resolved before the request is announced: a handler this wiring lacks aborts the run,
            // and a RequestStarted with no RequestFinished would leave a listener's test tree holding
            // a node that never ends.
            var handler = protocolRegistry.resolve(request.requestDefinition(), defaults.handlerNames(request));
            eventConsumer.accept(new RunEvent.RequestStarted(coordinates));
            var result = processor.process(request, defaults.options(request), coordinates, runtimeData, handler);
            resultConsumer.accept(result);
            eventConsumer.accept(new RunEvent.RequestFinished(result));
            pause(result.waitAfterMs());
            if (phase == SETUP && result.failed()) {
                return Optional.of(new SuiteStatus.Aborted("The setup request '" + request.name() + "' failed"));
            }
        }
        return Optional.empty();
    }

    /**
     * Pauses the walk after a request, ending early when the run is cancelled.
     *
     * @param millis how long to pause; {@code 0} or less pauses not at all
     * @throws BratException if the thread is interrupted while pausing; the interrupt flag is restored
     */
    private void pause(long millis) {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (!runControl.isCancelled()) {
            var remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return;
            }
            try {
                Thread.sleep(Duration.ofNanos(Math.min(remaining, PAUSE_SLICE.toNanos())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BratException("Interrupted while pausing after a request", e);
            }
        }
    }
}
