package dev.pbroman.brat.core.reporting;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.rendering.ReportText;
import dev.pbroman.brat.core.rendering.ResponseRenderer;

/**
 * Prints one run for {@link ConsoleRunReporter}, whose Javadoc describes the output.
 * <p>
 * Every count is kept as the events arrive; the result {@code RunFinished} carries is read only for
 * the run's duration and whether it was cancelled.
 */
final class ConsoleRunListener implements RunListener {

    /** What a request line opens with. */
    private enum Verdict {
        PASS,
        FAIL,
        SKIP,
        ERROR
    }

    private static final String INDENT = "  ";
    /** Puts the detail under a request beneath its name rather than its verdict. */
    private static final String DETAIL_INDENT = " ".repeat(9);

    private static final String RENDER_KIND = "console";
    private static final String DASH = " — ";

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
        var status = result.status();
        var failed = result.failed();
        var verdict =
                switch (status) {
                    case RequestStatus.Errored errored -> Verdict.ERROR;
                    case RequestStatus.Skipped skipped -> Verdict.SKIP;
                    default -> failed ? Verdict.FAIL : Verdict.PASS;
                };
        count(status, failed);

        var line = new StringBuilder(indent(depth(coordinates.path())))
                .append(column(verdict.name()))
                .append(coordinates.name())
                .append(tag(coordinates.phase()));
        if (!(status instanceof RequestStatus.Skipped)) {
            line.append(" (").append(duration(result.elapsedMs())).append(')');
        }
        var reason = reason(status);
        if (reason != null) {
            line.append(DASH).append(shown(reason));
        }
        out.println(line);
        ResponseRenderer.redirectNotice(result, mask, detail.showSecrets())
                .ifPresent(notice -> out.println(indent(depth(coordinates.path())) + DETAIL_INDENT + notice));

        for (var assertion : result.responseActionsResult().assertionResults()) {
            count(assertion);
        }
        capturesFailed += result.responseActionsResult().captureFailures().size();
        printDetail(result, indent(depth(coordinates.path())) + DETAIL_INDENT);
    }

    /**
     * Prints the detail under a request: the assertions {@code detail} shows, every capture failure, then
     * the request as sent and the response where {@code detail} shows them.
     *
     * @param result the request's result
     * @param detailIndent the indent the detail is printed at
     */
    private void printDetail(RequestResult result, String detailIndent) {
        for (var assertion : result.responseActionsResult().assertionResults()) {
            if (detail.showsAssertion(assertion)) {
                printAssertion(assertion, detailIndent);
            }
        }
        for (var capture : result.responseActionsResult().captureFailures()) {
            out.println(detailIndent + "CAPTURE " + capture.name() + DASH + shown(capture.message()));
        }
        if (detail.showsRequest(result)) {
            printSent(result, detailIndent);
        }
        if (detail.showsResponse(result)) {
            for (var line : ResponseRenderer.lines(result, mask, detail.showSecrets())) {
                out.println(detailIndent + line);
            }
        }
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

    private void printAssertion(AssertionResult assertion, String detailIndent) {
        String mark;
        if (assertion.passed()) {
            mark = "PASS";
        } else {
            mark = assertion.severity() == AssertionSeverity.WARN ? "WARN" : "FAIL";
        }
        var condition = assertion.condition();
        var header = detailIndent + String.format("%-6s", mark) + condition.getFunc();
        out.println(assertion.message() == null ? header : header + DASH + shown(assertion.message()));
        if (condition.isInterpolated()) {
            var outcomes = new LinkedHashMap<>(condition.getOutcomes());
            outcomes.remove("message");
            printRendered(new RenderTarget(outcomes), detailIndent + INDENT);
        }
    }

    private void printSent(RequestResult result, String detailIndent) {
        if (result.requestDefinition() instanceof ConfigData sent) {
            RenderTarget.of(sent).ifPresent(target -> {
                out.println(detailIndent + "request:");
                printRendered(new RenderTarget(target.outcomes()), detailIndent + INDENT);
            });
        }
    }

    /**
     * Renders {@code target} with every outcome's reporting string shown through {@link ReportText}, so it
     * is masked and cut before the renderer lays it out.
     *
     * @param target the outcomes to print
     * @param indent the indent of every line
     */
    private void printRendered(RenderTarget target, String indent) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        for (var entry : target.outcomes().entrySet()) {
            var outcome = entry.getValue();
            outcomes.put(
                    entry.getKey(),
                    new InterpolationOutcome(
                            outcome.value(), shown(outcome.reportingString()), outcome.containsSecret()));
        }
        renderer.render(RENDER_KIND, new RenderTarget(target.label(), outcomes))
                .lines()
                .map(line -> indent + line)
                .forEach(out::println);
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
                out.println(indent + "SKIPPED " + name + DASH + shown(skipped.reason()));
            }
            case SuiteStatus.Aborted aborted -> {
                suitesAborted++;
                out.println(indent + "ABORTED " + name + DASH + shown(aborted.reason()));
            }
            case SuiteStatus.Cancelled cancelled -> {
                suitesCancelled++;
                out.println(indent + "CANCELLED " + name);
            }
        }
    }

    private void summary(RunResult result) {
        out.println("-----");
        out.println(row("requests", requests)
                + part("passed", requestsPassed)
                + part("failed", requestsFailed)
                + part("skipped", requestsSkipped));
        out.println(row("assertions", assertions)
                + part("passed", assertionsPassed)
                + part("failed", assertionsFailed)
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
                parts.add(part("skipped", suitesSkipped));
            }
            if (suitesCancelled > 0) {
                parts.add(part("cancelled", suitesCancelled));
            }
            out.println(String.join("", parts));
        }
        String verdict;
        if (result.error() != null) {
            verdict = "ABORTED" + DASH + shown(result.error());
        } else if (result.cancelled()) {
            verdict = "CANCELLED";
        } else {
            verdict = requestsFailed > 0 || suitesAborted > 0 ? "FAILED" : "PASSED";
        }
        out.println(duration(result.elapsedMs()) + " · " + verdict);
    }

    private static String row(String label, int total) {
        return String.format("%-11s %4d", label, total);
    }

    private static String part(String label, int count) {
        return String.format("   %s %d", label, count);
    }

    private static String reason(RequestStatus status) {
        return switch (status) {
            case RequestStatus.Skipped skipped -> skipped.reason();
            case RequestStatus.Errored errored -> errored.message();
            case RequestStatus.GaveUp gaveUp -> gaveUp.message();
            case RequestStatus.Completed completed -> null;
        };
    }

    private static String tag(Phase phase) {
        return switch (phase) {
            case SETUP -> " [setup]";
            case TEARDOWN -> " [teardown]";
            case MAIN -> "";
        };
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
        return ms < 1000 ? ms + " ms" : String.format(Locale.ROOT, "%.1f s", ms / 1000.0);
    }
}
