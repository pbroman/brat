package dev.pbroman.brat.core.reporting;

import java.io.PrintStream;
import java.util.ArrayList;

import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.rendering.ReportMarkers;
import dev.pbroman.brat.core.rendering.ReportText;
import dev.pbroman.brat.core.rendering.RequestRenderer;

/**
 * Prints one run for {@link ConsoleRunReporter}, whose Javadoc describes the output.
 * <p>
 * Every count is kept as the events arrive; the result {@code RunFinished} carries is read only for
 * the run's duration and whether it was cancelled.
 */
final class ConsoleRunListener implements RunListener {

    private static final String INDENT = "  ";

    private static final String DASH = " — ";

    // The labels of the summary's counts, shared by its request, assertion and suite rows.
    private static final String COUNT_PASSED = "passed";
    private static final String COUNT_FAILED = "failed";
    private static final String COUNT_SKIPPED = "skipped";

    private final PrintStream out;
    private final OutcomeRenderer renderer;
    private final ReportDetail detail;
    /** The run's mask, from {@code RunStarted}; one that masks nothing until it arrives. */
    private ValueMask mask = new ValueMask();

    private int requests;
    private int requestsPassed;
    private int requestsFailed;
    private int requestsSkipped;
    private int assertions;
    private int assertionsPassed;
    private int assertionsFailed;
    private int assertionsWarned;
    private int capturesFailed;
    private int suites;
    private int suitesAborted;
    private int suitesSkipped;
    private int suitesCancelled;

    /**
     * Constructs a listener for one run.
     *
     * @param out the stream to print to
     * @param renderer renders interpolation outcomes
     * @param detail what to show under each request
     */
    ConsoleRunListener(PrintStream out, OutcomeRenderer renderer, ReportDetail detail) {
        this.out = out;
        this.renderer = renderer;
        this.detail = detail;
    }

    @Override
    public void on(RunEvent event) {
        switch (event) {
            // the first suite header is the start, so nothing prints
            case RunEvent.RunStarted started -> mask = started.mask();
            case RunEvent.SuiteEntered entered -> out.println(indent(depth(entered.path())) + entered.name());
            case RunEvent.RequestStarted started -> {
                // a request prints once, when it has finished
            }
            case AttemptFinished attempt -> attemptFinished(attempt);
            case RunEvent.RequestFinished finished -> requestFinished(finished.result());
            case RunEvent.SuiteExited exited -> suiteExited(exited);
            case RunEvent.RunFinished finished -> summary(finished.result());
        }
        out.flush();
    }

    private void attemptFinished(AttemptFinished attempt) {
        var coordinates = attempt.coordinates();
        String outcome;
        if (attempt.error() != null) {
            outcome = shown(attempt.error());
        } else {
            outcome = attempt.conditionMet() ? "condition met" : "condition not met";
        }
        out.println(indent(depth(coordinates.path())) + column("...") + coordinates.name() + DASH + "attempt "
                + attempt.attempt() + "/" + attempt.maxAttempts() + ", " + outcome + " ("
                + duration(attempt.roundTripTimeMs()) + ")");
    }

    private void requestFinished(RequestResult result) {
        var coordinates = result.coordinates();
        count(result.status(), result.failed());

        out.println(indent(depth(coordinates.path())) + RequestRenderer.headline(result, mask, detail.showSecrets()));
        var detailIndent = indent(depth(coordinates.path())) + RequestRenderer.DETAIL_INDENT;
        for (var detailLine : RequestRenderer.lines(result, detail, mask, renderer)) {
            out.println(detailIndent + detailLine);
        }

        for (var assertion : result.responseActionsResult().assertionResults()) {
            count(assertion);
        }
        capturesFailed += result.responseActionsResult().captureFailures().size();
    }

    private void count(RequestStatus status, boolean failed) {
        requests++;
        if (status instanceof RequestStatus.Skipped) {
            requestsSkipped++;
        } else if (failed) {
            requestsFailed++;
        } else {
            requestsPassed++;
        }
    }

    private void count(AssertionResult assertion) {
        assertions++;
        if (assertion.passed()) {
            assertionsPassed++;
        } else if (assertion.severity() == AssertionSeverity.WARN) {
            assertionsWarned++;
        } else {
            assertionsFailed++;
        }
    }

    /**
     * {@code text} as the report prints it: masked with the run's mask, then cut.
     *
     * @param text a value from the run's data
     * @return the text to print
     */
    private String shown(String text) {
        return ReportText.shown(text, mask, detail.showSecrets());
    }

    private void suiteExited(RunEvent.SuiteExited exited) {
        suites++;
        var name = exited.path().substring(exited.path().lastIndexOf('/') + 1);
        var indent = indent(depth(exited.path()) + 1);
        switch (exited.status()) {
            case SuiteStatus.Completed completed -> {
                // a completed suite needs no line of its own
            }
            case SuiteStatus.Skipped skipped -> {
                suitesSkipped++;
                out.println(indent + ReportMarkers.SKIPPED + " " + name + DASH + shown(skipped.reason()));
            }
            case SuiteStatus.Aborted aborted -> {
                suitesAborted++;
                out.println(indent + ReportMarkers.ABORTED + " " + name + DASH + shown(aborted.reason()));
            }
            case SuiteStatus.Cancelled cancelled -> {
                suitesCancelled++;
                out.println(indent + ReportMarkers.CANCELLED + " " + name);
            }
        }
    }

    private void summary(RunResult result) {
        out.println("-----");
        out.println(row("requests", requests)
                + part(COUNT_PASSED, requestsPassed)
                + part(COUNT_FAILED, requestsFailed)
                + part(COUNT_SKIPPED, requestsSkipped));
        out.println(row("assertions", assertions)
                + part(COUNT_PASSED, assertionsPassed)
                + part(COUNT_FAILED, assertionsFailed)
                + part("warned", assertionsWarned));
        if (capturesFailed > 0) {
            out.println("captures failed " + capturesFailed);
        }
        if (suitesAborted + suitesSkipped + suitesCancelled > 0) {
            var parts = new ArrayList<String>();
            parts.add(row("suites", suites));
            if (suitesAborted > 0) {
                parts.add(part("aborted", suitesAborted));
            }
            if (suitesSkipped > 0) {
                parts.add(part(COUNT_SKIPPED, suitesSkipped));
            }
            if (suitesCancelled > 0) {
                parts.add(part("cancelled", suitesCancelled));
            }
            out.println(String.join("", parts));
        }
        String verdict;
        if (result.error() != null) {
            verdict = ReportMarkers.ABORTED + DASH + shown(result.error());
        } else if (result.cancelled()) {
            verdict = ReportMarkers.CANCELLED;
        } else {
            verdict = requestsFailed > 0 || suitesAborted > 0 ? ReportMarkers.FAILED : ReportMarkers.PASSED;
        }
        out.println(duration(result.elapsedMs()) + " · " + verdict);
    }

    private static String row(String label, int total) {
        return String.format("%-11s %4d", label, total);
    }

    private static String part(String label, int count) {
        return String.format("   %s %d", label, count);
    }

    /** Pads a request line's marker so that the names after it line up. */
    private static String column(String marker) {
        return String.format("%-7s", marker);
    }

    private static int depth(String path) {
        var depth = 0;
        for (var i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                depth++;
            }
        }
        return depth;
    }

    private static String indent(int depth) {
        return INDENT.repeat(depth);
    }

    private static String duration(long ms) {
        return ReportText.duration(ms);
    }
}
