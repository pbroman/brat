package dev.pbroman.brat.junit;

import java.io.PrintStream;

import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.rendering.ReportMarkers;
import dev.pbroman.brat.core.rendering.ReportText;
import dev.pbroman.brat.core.rendering.RequestRenderer;

/**
 * Static helpers printing what a run's tests print, each while it executes, so that whatever captures
 * the test's output — an IDE, Surefire — files it with that test.
 */
final class TestPrinter {

    private TestPrinter() {
        // static helpers only
    }

    /**
     * Prints a request's test: nothing if {@code output} is quiet; otherwise the request's line as
     * {@code brat run} prints it ({@code RequestRenderer.headline}), then — for a request that did not
     * fail — the lines {@code output}'s detail shows beneath it ({@code RequestRenderer.lines}), each
     * preceded by {@code RequestRenderer.DETAIL_INDENT}. A failed request's detail is in its failure's
     * message, and is not printed a second time.
     *
     * @param out where to print; never {@code null}
     * @param result the request's result; never {@code null}
     * @param output what to print; never {@code null}
     * @param mask the run's mask; never {@code null}
     * @param renderer renders interpolation outcomes; never {@code null}
     */
    static void request(
            PrintStream out, RequestResult result, Output output, ValueMask mask, OutcomeRenderer renderer) {
        if (output.quiet()) {
            return;
        }
        var showSecrets = output.detail().showSecrets();
        out.println(RequestRenderer.headline(result, mask, showSecrets));
        if (!result.failed()) {
            for (var line : RequestRenderer.lines(result, output.detail(), mask, renderer)) {
                out.println(RequestRenderer.DETAIL_INDENT + line);
            }
        }
        out.flush();
    }

    /**
     * Prints the line of a test standing for a suite's end or the run's — {@code ABORTED <name> — <reason>},
     * {@code SKIPPED <name> — <reason>}, {@code ERROR <name> — <reason>} for a run that failed — the
     * reason shown through {@code ReportText.shown}; nothing if {@code output} is quiet.
     *
     * @param out where to print; never {@code null}
     * @param verdict {@link ReportMarkers#ABORTED}, {@link ReportMarkers#SKIPPED} or
     *        {@link ReportMarkers#ERROR}; never {@code null}
     * @param name the suite's name; never {@code null}
     * @param reason why; never {@code null}
     * @param output what to print; never {@code null}
     * @param mask the run's mask; never {@code null}
     */
    static void end(PrintStream out, String verdict, String name, String reason, Output output, ValueMask mask) {
        if (output.quiet()) {
            return;
        }
        out.println(verdict + " " + name + " — "
                + ReportText.shown(reason, mask, output.detail().showSecrets()));
        out.flush();
    }
}
