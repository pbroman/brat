package dev.pbroman.brat.core.reporting;

import java.io.PrintStream;
import java.util.Map;

import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ReporterContext;
import dev.pbroman.brat.core.api.reporting.RunReporter;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.rendering.ReportText;
import dev.pbroman.brat.core.rendering.ResponseRenderer;

import static dev.pbroman.brat.core.api.util.ArgsUtils.rejectUnknownArgs;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The core run reporter: prints a run to a stream as it happens, then a summary of it.
 * <p>
 * Selected as {@value #NAME}. Core registers one over {@code System.out}; registering another under the
 * same name — over a different stream — redirects it.
 *
 * <pre>
 * users
 *   PASS   login [setup] (120 ms)
 *   FAIL   read user (38 ms)
 *            FAIL  isEqualTo — name must match
 *              a: ${response.json.$.name} → bob
 *              b: alice
 *            request:
 *              url: ${env.baseUrl}/users/42 → http://localhost:8080/users/42
 *              header.Authorization: Bearer ${secrets.token} → Bearer ***
 *            response: 200
 *              Content-Type: application/json
 *              body:
 *                {
 *                  "name" : "bob"
 *                }
 *   SKIP   delete user — Skipped due to condition …
 *   polling
 *     ...    poll job — attempt 1/10, condition not met (12 ms)
 *     PASS   poll job (2.1 s)
 * admin
 *   FAIL   login [setup] (40 ms)
 *   ABORTED admin — The setup request 'login' failed
 * -----
 * requests       5   passed 2   failed 2   skipped 1
 * assertions     9   passed 7   failed 1   warned 1
 * suites         3   aborted 1
 * 2.9 s · FAILED
 * </pre>
 *
 * <strong>What each event prints.</strong> Indentation is two spaces per level of the path, so a
 * suite's requests sit one level below its name.
 * <ul>
 *   <li>{@code RunStarted} — nothing.</li>
 *   <li>{@code SuiteEntered} — the suite's name.</li>
 *   <li>{@code AttemptFinished} — one line per attempt of a polling request: the attempt number out of
 *       the maximum, whether the condition was met or the attempt's error, and its round-trip time.</li>
 *   <li>{@code RequestFinished} — one line: a verdict, the request's name, {@code [setup]} or
 *       {@code [teardown]} for a request outside the main phase, the time unless it was skipped, and
 *       the reason or message of a skipped, errored or given-up request. The verdict is {@code ERROR}
 *       for a request that could not be performed, {@code SKIP} for a skipped one, {@code FAIL} for any
 *       other that {@linkplain RequestResult#failed() failed} — a poll
 *       that gave up included — and {@code PASS} otherwise. Beneath it, the detail (below).</li>
 *   <li>{@code SuiteExited} — nothing for a completed suite; otherwise {@code SKIPPED},
 *       {@code ABORTED} or {@code CANCELLED}, the suite's name, and the reason where there is one.</li>
 *   <li>{@code RunFinished} — the summary.</li>
 * </ul>
 * <strong>The detail under a request</strong> is chosen by the arguments, read as a {@link ReportDetail}:
 * <ul>
 *   <li>the assertions it shows — by default every assertion that did not pass, of either severity, so
 *       a failed {@code WARN} appears even under {@code PASS}; with {@code assertions: all}, every one;</li>
 *   <li>every capture failure, always;</li>
 *   <li>the request as sent, labelled {@code request:}, where it shows it — by default under
 *       {@code FAIL} and {@code ERROR}; it prints only when its definition was interpolated, since a
 *       request whose definition could not be interpolated has nothing to show;</li>
 *   <li>the response, below the request as sent and as {@link ResponseRenderer} renders it, where it
 *       shows it — by default under {@code FAIL}; an {@code ERROR} never has one.</li>
 * </ul>
 * An assertion prints as its result ({@code PASS}, {@code FAIL} or {@code WARN}), its func and its
 * message, then its outcomes other than {@code message} — none for an assertion that could not be
 * interpolated. A capture failure prints as {@code CAPTURE}, the variable and the message. Outcomes are
 * rendered with the {@code console} render kind.
 * <p>
 * <strong>The summary</strong> counts what was evaluated, as the events arrived:
 * <ul>
 *   <li>{@code requests} — the total, then passed, failed and skipped, where failed includes errored
 *       and given-up requests;</li>
 *   <li>{@code assertions} — one per assertion result, so each link of a chain counts: the total, then
 *       passed, failed ({@code FAIL} severity) and warned ({@code WARN} severity, not passed). The
 *       assertions of a request that never ran are not counted;</li>
 *   <li>{@code captures failed} — only when there was one;</li>
 *   <li>{@code suites} — the total, then whichever of aborted, skipped and cancelled is not zero;
 *       only when some suite did not complete;</li>
 *   <li>the run's duration and its verdict: {@code ABORTED} and what ended it for a run a structural
 *       failure ended early, {@code CANCELLED} for a cancelled run, {@code FAILED} if a request failed
 *       or a suite was aborted, {@code PASSED} otherwise.</li>
 * </ul>
 * <strong>Durations</strong> below a second print in milliseconds ({@code 120 ms}), from a second up
 * in seconds with one decimal ({@code 2.1 s}).
 * <p>
 * <strong>Nothing printed holds a secret.</strong> Every outcome, header, body, reason and message
 * passes through {@link ReportText#shown} with the run's {@link ValueMask} from {@code RunStarted}, so a
 * value a secrets provider returned is masked wherever it appears, and so is a value that only looks
 * sensitive — unless {@code secrets: show} was given, which lifts that second kind only. Each value is
 * also cut to {@link ReportText#MAX_LENGTH} characters.
 * <p>
 * The stream is flushed after every event, so the output stays live when the stream buffers.
 */
public final class ConsoleRunReporter implements RunReporter {

    /** The name this reporter is selected by. */
    public static final String NAME = "console";

    private final PrintStream out;

    /**
     * Constructs a console reporter printing to {@code out}.
     *
     * @param out the stream to print to; must not be {@code null}. Not closed by the reporter
     * @throws BratException if {@code out} is {@code null}
     */
    public ConsoleRunReporter(PrintStream out) {
        nonNull(out, "The stream a console reporter prints to must not be null");
        this.out = out;
    }

    @Override
    public String name() {
        return NAME;
    }

    /**
     * Creates the listener printing one run.
     * <p>
     * The arguments are those of {@link ReportDetail} — {@code response}, {@code request},
     * {@code assertions} and {@code secrets} — and nothing else.
     *
     * @param context this run's arguments and the renderer; never {@code null}
     * @return a new listener for one run
     * @throws BratException if {@code context.args()} holds a key outside {@link ReportDetail#KEYS}, or
     *         a value {@link ReportDetail#of(Map)} rejects
     */
    @Override
    public RunListener create(ReporterContext context) {
        rejectUnknownArgs(context.args(), "the '" + NAME + "' reporter", ReportDetail.KEYS.toArray(String[]::new));
        return new ConsoleRunListener(out, context.renderer(), ReportDetail.of(context.args()));
    }
}
