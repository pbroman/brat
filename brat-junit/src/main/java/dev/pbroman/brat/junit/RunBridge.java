package dev.pbroman.brat.junit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ReporterContext;
import dev.pbroman.brat.core.api.reporting.RunReporter;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.launch.PreparedRun;
import dev.pbroman.brat.core.rendering.ReportText;
import dev.pbroman.brat.core.rendering.RequestRenderer;
import dev.pbroman.brat.core.runner.Brat;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

/**
 * Turns one run of a suite into the dynamic tests JUnit executes, as the run happens: the bridge behind
 * {@link BratTests#tests()}, whose Javadoc states what the tests are.
 */
final class RunBridge {

    /** The name the bridge's reporter is registered under on the runner it builds. */
    static final String REPORTER = "brat-junit";

    private static final String DASH = " — ";
    private static final String INDENT = "  ";

    private RunBridge() {
        // static entry point only
    }

    /**
     * Builds a runner as {@code brat run} does — the plugins on the classpath discovered — with the
     * bridge's reporter added, through which a run's events and the runner's outcome renderer reach the
     * bridge.
     *
     * @return the runner, for {@link #nodes} to take over
     */
    static Brat newBrat() {
        return Brat.builder().runReporter(new Reporter()).build();
    }

    /**
     * Returns the tests of {@code prepared}'s run, as {@link BratTests#tests()} describes them.
     * <p>
     * The run starts on a daemon thread when the stream's first element is requested, with a listener
     * from the bridge's reporter and a run control of its own; each event goes to a queue. Whichever of
     * JUnit's threads reads the queue sorts each event to where it belongs — a request's result to that
     * request's test, a suite's events to that suite's level — so the tests may be consumed and executed
     * in any order, on any number of threads. {@code brat} is closed when the run ends, or when the
     * stream is closed before the run started.
     *
     * @param brat a runner from {@link #newBrat()}, owned from here on; never {@code null}
     * @param prepared the suite and its environment, read by {@code brat}; never {@code null}
     * @return the tests
     */
    static Stream<DynamicNode> nodes(Brat brat, PreparedRun prepared) {
        var run = new Run(brat, prepared);
        return stream(new Level(run, prepared.suite().name(), Run.TOP, true)).onClose(run::close);
    }

    private static Stream<DynamicNode> stream(Iterator<DynamicNode> nodes) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(nodes, Spliterator.ORDERED), false);
    }

    /**
     * One run: its thread, its control, and its events, sorted as they are read to where they belong — a
     * request's result to that request's test, a suite's events to that suite's level — so that JUnit
     * may consume the tests in any order and on any number of threads.
     */
    private static final class Run {

        /** The key of the level the suite's own {@code SuiteEntered} goes to, before its path is known. */
        private static final String TOP = "";

        private final Brat brat;
        private final PreparedRun prepared;
        private final Events events;
        private final RunControl control = RunControl.create();
        /** Held by whichever thread is reading the queue, so each event is read and sorted once. */
        private final ReentrantLock reading = new ReentrantLock();

        private final Map<String, Queue<RunEvent>> byPath = new ConcurrentHashMap<>();
        private final Map<Integer, CompletableFuture<RequestResult>> results = new ConcurrentHashMap<>();
        private boolean started;
        /** Set once {@code RunFinished} has been read. */
        private volatile boolean ended;
        /** Set once the run was stopped early: nothing more is handed over. */
        private volatile boolean stopped;
        /** The run's error, until the suite's own level has reported it. */
        private volatile String error;

        private volatile ValueMask mask = new ValueMask();

        Run(Brat brat, PreparedRun prepared) {
            this.brat = brat;
            this.prepared = prepared;
            this.events = (Events) brat.reporter(REPORTER, Map.of());
        }

        /**
         * The next structural event for the level at {@code path} — a sub-suite entered, a request
         * started, the suite exited — reading the run until there is one; {@code null} once the run has
         * ended or was stopped. An interruption while waiting stops the run, waits for it, and is thrown
         * on.
         */
        RunEvent nextFor(String path) throws InterruptedException {
            var pending = queueOf(path);
            while (!stopped) {
                var event = pending.poll();
                if (event != null) {
                    return event;
                }
                if (ended) {
                    return null;
                }
                readOneUnless(() -> !pending.isEmpty());
            }
            return null;
        }

        /**
         * The result of request {@code requestNo}, reading the run until it arrives; {@code null} if the
         * run ended without it. An interruption while waiting stops the run, waits for it, and is thrown
         * on.
         */
        RequestResult resultOf(int requestNo) throws InterruptedException {
            var result = futureOf(requestNo);
            while (!result.isDone()) {
                readOneUnless(result::isDone);
            }
            return result.join();
        }

        /**
         * Reads and sorts one event, unless the run has ended or {@code satisfied} already holds once the
         * reading lock is held — another thread may have read what the caller waits for meanwhile.
         */
        private void readOneUnless(BooleanSupplier satisfied) throws InterruptedException {
            start();
            try {
                reading.lockInterruptibly();
                try {
                    if (ended || satisfied.getAsBoolean()) {
                        return;
                    }
                    sort(events.queue().take());
                } finally {
                    reading.unlock();
                }
            } catch (InterruptedException e) {
                stopAndWait();
                throw e;
            }
        }

        private void sort(RunEvent event) {
            switch (event) {
                case RunEvent.RunStarted runStarted -> mask = runStarted.mask();
                case RunEvent.SuiteEntered entered ->
                    queueOf(parentOf(entered.path())).add(entered);
                case RunEvent.RequestStarted started ->
                    queueOf(parentOf(started.coordinates().path())).add(started);
                case RunEvent.RequestFinished finished ->
                    futureOf(finished.result().coordinates().requestNo()).complete(finished.result());
                case RunEvent.SuiteExited exited -> queueOf(exited.path()).add(exited);
                case RunEvent.RunFinished finished -> {
                    error = finished.result().error();
                    ended = true;
                    for (var result : results.values()) {
                        result.complete(null);
                    }
                }
                default -> {
                    // an attempt of a polled request: nothing to hand over
                }
            }
        }

        private Queue<RunEvent> queueOf(String path) {
            return byPath.computeIfAbsent(path, key -> new ConcurrentLinkedQueue<>());
        }

        private CompletableFuture<RequestResult> futureOf(int requestNo) {
            var result = results.computeIfAbsent(requestNo, key -> new CompletableFuture<>());
            if (ended) {
                result.complete(null);
            }
            return result;
        }

        private static String parentOf(String path) {
            var slash = path.lastIndexOf('/');
            return slash < 0 ? TOP : path.substring(0, slash);
        }

        private synchronized void start() {
            if (started) {
                return;
            }
            started = true;
            var thread = new Thread(this::run, "brat-junit " + prepared.suite().name());
            thread.setDaemon(true);
            thread.start();
        }

        private void run() {
            try {
                brat.run(prepared.suite(), prepared.environment(), List.of(events), control);
            } catch (RuntimeException | Error e) {
                if (!events.finished()) {
                    // failed before the walk could report its end: report it as the end
                    events.on(new RunEvent.RunFinished(new RunResult(
                            List.of(), List.of(), 0, false, e.getClass().getSimpleName() + ": " + e.getMessage())));
                }
            } finally {
                brat.close();
            }
        }

        /** Asks the run to stop and reads its events until it has ended; nothing more is handed over. */
        void stopAndWait() {
            stopped = true;
            control.stop();
            var interrupted = false;
            while (!ended) {
                reading.lock();
                try {
                    if (!ended) {
                        sort(events.queue().take());
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    reading.unlock();
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        /** What the stream's {@code close} does: close an unstarted runner, stop a run under way. */
        synchronized void close() {
            if (!started) {
                started = true;
                ended = true;
                stopped = true;
                brat.close();
            } else if (!ended) {
                stopAndWait();
            }
        }

        String shown(String text) {
            return ReportText.shown(text, mask, false);
        }
    }

    /**
     * The nodes of one suite, read from the run as JUnit asks for them: a sub-suite's up to its
     * {@code SuiteExited}, the suite's own up to the run's end.
     */
    private static final class Level implements Iterator<DynamicNode> {

        private final Run run;
        private final String name;
        private final boolean root;
        /** The suite's path; the suite's own level learns it from its {@code SuiteEntered}. */
        private String path;

        private DynamicNode next;
        private boolean exhausted;

        Level(Run run, String name, String path, boolean root) {
            this.run = run;
            this.name = name;
            this.path = path;
            this.root = root;
        }

        @Override
        public boolean hasNext() {
            if (next == null && !exhausted) {
                next = computeNext();
                if (next == null) {
                    exhausted = true;
                }
            }
            return next != null;
        }

        @Override
        public DynamicNode next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            var node = next;
            next = null;
            return node;
        }

        private DynamicNode computeNext() {
            try {
                while (!exhausted) {
                    var event = run.nextFor(path);
                    if (event == null) {
                        return endOfRun();
                    }
                    var node = nodeFor(event);
                    if (node != null) {
                        return node;
                    }
                }
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exhausted = true;
                return null;
            }
        }

        /** The node an event stands for at this level, or {@code null}; marks the level's end. */
        private DynamicNode nodeFor(RunEvent event) {
            if (event instanceof RunEvent.SuiteEntered entered) {
                if (root && path.equals(Run.TOP)) {
                    path = entered.path();
                    return null;
                }
                var child = new Level(run, entered.name(), entered.path(), false);
                return DynamicContainer.dynamicContainer(entered.name(), stream(child));
            }
            if (event instanceof RunEvent.RequestStarted started) {
                var coordinates = started.coordinates();
                var requestNo = coordinates.requestNo();
                return DynamicTest.dynamicTest(
                        coordinates.name() + tag(coordinates.phase()), () -> execute(run, requestNo));
            }
            if (event instanceof RunEvent.SuiteExited exited) {
                // a sub-suite ends here; the suite itself still reads on, to the run's end and its error
                exhausted = !root;
                return suiteEnd(exited.status());
            }
            return null;
        }

        /** The test a suite's end adds — aborted or skipped — or {@code null}. */
        private DynamicNode suiteEnd(SuiteStatus status) {
            if (status instanceof SuiteStatus.Aborted aborted) {
                var reason = run.shown(aborted.reason());
                return DynamicTest.dynamicTest(name + ": aborted", () -> {
                    throw new AssertionFailedError(reason);
                });
            }
            if (status instanceof SuiteStatus.Skipped skipped) {
                var reason = run.shown(skipped.reason());
                return DynamicTest.dynamicTest(name + ": skipped", () -> {
                    throw new TestAbortedException(reason);
                });
            }
            return null;
        }

        /** At the run's end: a sub-suite just ends; the suite itself reports a run error, once. */
        private DynamicNode endOfRun() {
            exhausted = true;
            if (!root || run.stopped || run.error == null) {
                return null;
            }
            var error = run.shown(run.error);
            run.error = null;
            return DynamicTest.dynamicTest(name + ": run failed", () -> {
                throw new AssertionFailedError(error);
            });
        }
    }

    /** A request's test: waits for that request's result and turns it into JUnit's verdict. */
    private static void execute(Run run, int requestNo) {
        RequestResult result;
        try {
            result = run.resultOf(requestNo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionFailedError("Interrupted while the request ran; the run was stopped", e);
        }
        if (result == null) {
            throw new AssertionFailedError("The run ended before this request finished");
        }
        verdict(result, run);
    }

    /** Passes, or throws what JUnit reports for {@code result}. */
    private static void verdict(RequestResult result, Run run) {
        var status = result.status();
        if (status instanceof RequestStatus.Skipped skipped) {
            throw new TestAbortedException(run.shown(skipped.reason()));
        }
        if (!result.failed()) {
            return;
        }
        String reason = null;
        if (status instanceof RequestStatus.Errored errored) {
            reason = errored.message();
        } else if (status instanceof RequestStatus.GaveUp gaveUp) {
            reason = gaveUp.message();
        }
        var lines = new ArrayList<String>();
        var header = result.coordinates().name();
        lines.add(reason == null ? header : header + DASH + run.shown(reason));
        var detail = RequestRenderer.lines(result, ReportDetail.of(Map.of()), run.mask, run.events.renderer());
        for (var line : detail) {
            lines.add(INDENT + line);
        }
        throw new AssertionFailedError(String.join(System.lineSeparator(), lines));
    }

    private static String tag(Phase phase) {
        return switch (phase) {
            case SETUP -> " [setup]";
            case TEARDOWN -> " [teardown]";
            case MAIN -> "";
        };
    }

    /** Creates, per run, the listener putting the run's events on a queue the bridge reads. */
    private static final class Reporter implements RunReporter {

        @Override
        public String name() {
            return REPORTER;
        }

        @Override
        public RunListener create(ReporterContext context) {
            return new Events(context.renderer());
        }
    }

    /**
     * One run's events, in the order they were emitted, and the renderer of the runner that emitted them.
     */
    static final class Events implements RunListener {

        private final BlockingQueue<RunEvent> queue = new LinkedBlockingQueue<>();
        private final OutcomeRenderer renderer;
        private volatile boolean finished;

        Events(OutcomeRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public void on(RunEvent event) {
            if (event instanceof RunEvent.RunFinished) {
                finished = true;
            }
            queue.add(event);
        }

        /**
         * Returns the queue the events arrive on.
         *
         * @return the queue
         */
        BlockingQueue<RunEvent> queue() {
            return queue;
        }

        /**
         * Returns the renderer of the runner that created this listener.
         *
         * @return the renderer
         */
        OutcomeRenderer renderer() {
            return renderer;
        }

        /**
         * Returns whether {@code RunFinished} has arrived.
         *
         * @return whether the run has reported its end
         */
        boolean finished() {
            return finished;
        }
    }
}
