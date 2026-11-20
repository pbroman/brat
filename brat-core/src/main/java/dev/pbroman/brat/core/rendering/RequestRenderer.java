package dev.pbroman.brat.core.rendering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.api.rendering.RenderKinds;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Renders what a report shows beneath one request, as lines of text: the detail a {@link ReportDetail}
 * asks for.
 * <p>
 * The console report prints these lines under each request's own line; anything else reporting one
 * request — a test's failure message — shows the same.
 */
public final class RequestRenderer {

    private static final String INDENT = "  ";

    private static final String DASH = " — ";

    /** The outcome key of an assertion's message, which its header line already shows. */
    private static final String MESSAGE = "message";

    private RequestRenderer() {
        // static helpers only
    }

    /**
     * Returns the lines a report prints beneath a request's own line, in this order:
     * <ol>
     *   <li>the notice of a redirect that was not followed, if there is one
     *       ({@link ResponseRenderer#redirectNotice});</li>
     *   <li>each assertion {@code detail} shows ({@link ReportDetail#showsAssertion}), in the order they
     *       ran: a line of its mark — {@code PASS}; {@code WARN} for a failed {@code WARN} assertion;
     *       {@code FAIL} — padded to six characters, then its func, then {@code " — "} and its message
     *       where it has one. For an assertion whose condition was interpolated, the condition's
     *       outcomes follow, its {@code message} left out, as {@code renderer} renders them for
     *       {@link RenderKinds#CONSOLE}, each line indented by two spaces;</li>
     *   <li>each capture failure, in order: {@code CAPTURE <name> — <message>};</li>
     *   <li>where {@code detail} shows the request ({@link ReportDetail#showsRequest}) and the definition
     *       as sent is an interpolated copy: a line {@code request:}, then its outcomes as
     *       {@code renderer} renders them for {@link RenderKinds#CONSOLE}, each line indented by two
     *       spaces;</li>
     *   <li>where {@code detail} shows the response ({@link ReportDetail#showsResponse}), the lines of
     *       {@link ResponseRenderer#lines}.</li>
     * </ol>
     * <strong>Every value taken from the run</strong> — a message, an outcome's reporting string —
     * passes through {@link ReportText#shown} with {@code mask} and {@link ReportDetail#showSecrets()},
     * so it is masked and cut before it is laid out. The lines carry no indent but the nesting above; a
     * caller indents them where it prints them.
     *
     * @param result the request's result; must not be {@code null}
     * @param detail what to show; must not be {@code null}
     * @param mask the run's mask; must not be {@code null}
     * @param renderer renders interpolation outcomes; must not be {@code null}
     * @return the lines, empty when there is nothing to show; never {@code null}
     * @throws BratException if any argument is {@code null}
     */
    public static List<String> lines(
            RequestResult result, ReportDetail detail, ValueMask mask, OutcomeRenderer renderer) {
        nonNull(result, "The request result to render must not be null");
        nonNull(detail, "The report detail must not be null");
        nonNull(mask, "The mask must not be null");
        nonNull(renderer, "The outcome renderer must not be null");
        var lines = new ArrayList<String>();
        ResponseRenderer.redirectNotice(result, mask, detail.showSecrets()).ifPresent(lines::add);
        for (var assertion : result.responseActionsResult().assertionResults()) {
            if (detail.showsAssertion(assertion)) {
                lines.addAll(assertionLines(assertion, detail, mask, renderer));
            }
        }
        for (var capture : result.responseActionsResult().captureFailures()) {
            lines.add("CAPTURE " + capture.name() + DASH
                    + ReportText.shown(capture.message(), mask, detail.showSecrets()));
        }
        if (detail.showsRequest(result) && result.requestDefinition() instanceof ConfigData sent) {
            var target = RenderTarget.of(sent);
            if (target.isPresent()) {
                lines.add("request:");
                lines.addAll(rendered(new RenderTarget(target.get().outcomes()), detail, mask, renderer));
            }
        }
        if (detail.showsResponse(result)) {
            lines.addAll(ResponseRenderer.lines(result, mask, detail.showSecrets()));
        }
        return List.copyOf(lines);
    }

    private static List<String> assertionLines(
            AssertionResult assertion, ReportDetail detail, ValueMask mask, OutcomeRenderer renderer) {
        String mark;
        if (assertion.passed()) {
            mark = "PASS";
        } else {
            mark = assertion.severity() == AssertionSeverity.WARN ? "WARN" : "FAIL";
        }
        var condition = assertion.condition();
        var header = String.format("%-6s", mark) + condition.getFunc();
        var lines = new ArrayList<String>();
        lines.add(
                assertion.message() == null
                        ? header
                        : header + DASH + ReportText.shown(assertion.message(), mask, detail.showSecrets()));
        if (condition.isInterpolated()) {
            var outcomes = new LinkedHashMap<>(condition.getOutcomes());
            outcomes.remove(MESSAGE);
            lines.addAll(rendered(new RenderTarget(outcomes), detail, mask, renderer));
        }
        return lines;
    }

    /**
     * Renders {@code target} with every outcome's reporting string shown through {@link ReportText}, so
     * it is masked and cut before the renderer lays it out, each line indented by two spaces.
     */
    private static List<String> rendered(
            RenderTarget target, ReportDetail detail, ValueMask mask, OutcomeRenderer renderer) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        for (var entry : target.outcomes().entrySet()) {
            var outcome = entry.getValue();
            outcomes.put(
                    entry.getKey(),
                    new InterpolationOutcome(
                            outcome.value(),
                            ReportText.shown(outcome.reportingString(), mask, detail.showSecrets()),
                            outcome.containsSecret()));
        }
        return renderer.render(RenderKinds.CONSOLE, new RenderTarget(target.label(), outcomes))
                .lines()
                .map(line -> INDENT + line)
                .toList();
    }
}
