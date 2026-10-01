package dev.pbroman.brat.core.reporting;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.reporting.ReporterContext;
import dev.pbroman.brat.core.data.AssertionSeverity;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.result.CaptureFailure;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteStatus;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.rendering.OutcomeRendererRuleDispatcher;
import dev.pbroman.brat.core.rendering.rules.ConsoleOutcomeRendererRule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Drives the console listener with events directly and reads what it printed. Asserts on content, not
 * on columns: whitespace runs are collapsed before comparing, except where indentation is the point.
 */
class ConsoleRunReporterTest {

    private static final String SECRET = "s3cr3t";

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final ConsoleRunReporter underTest =
            new ConsoleRunReporter(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    private final OutcomeRendererRuleDispatcher renderer =
            new OutcomeRendererRuleDispatcher(List.of(new ConsoleOutcomeRendererRule()));
    private int requestNo;

    // --- fixtures ---

    private RunListener listener(Map<String, String> args) {
        return underTest.create(new ReporterContext(args, renderer));
    }

    private RunListener listener() {
        return listener(Map.of());
    }

    private String printed() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    /** The output with every run of spaces collapsed to one, for content assertions. */
    private String collapsed() {
        return printed().replaceAll(" +", " ");
    }

    private List<String> lines() {
        return printed().lines().toList();
    }

    private RequestCoordinates coordinates(String path, Phase phase) {
        var name = path.substring(path.lastIndexOf('/') + 1);
        return new RequestCoordinates(path, null, name, phase, ++requestNo);
    }

    private static HttpRequestDefinition sent() {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put(
                "url", new InterpolationOutcome("http://h/users/42", "${env.baseUrl}/users/42 → http://h/users/42"));
        outcomes.put(
                "header.Authorization",
                new InterpolationOutcome("Bearer " + SECRET, "Bearer ${secrets.token} → Bearer ***", true));
        return new HttpRequestDefinition(
                "http://h/users/42", "GET", null, Map.of("Authorization", "Bearer " + SECRET), null, outcomes);
    }

    private static HttpRequestDefinition authored() {
        return new HttpRequestDefinition("${env.baseUrl}/users/42", "GET", null, null);
    }

    private static RequestStatus completed() {
        return new RequestStatus.Completed(Map.of(), 1, 10);
    }

    private RequestResult result(String path, RequestStatus status, long elapsedMs, AssertionResult... assertions) {
        return new RequestResult(
                coordinates(path, Phase.MAIN),
                sent(),
                null,
                status,
                elapsedMs,
                0,
                new ResponseActionsResult(List.of(assertions), List.of()));
    }

    private static AssertionResult assertion(String func, boolean passed, AssertionSeverity severity) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("a", new InterpolationOutcome("bob", "${response.json.$.name} → bob"));
        outcomes.put("b", new InterpolationOutcome("alice", "alice"));
        outcomes.put("message", new InterpolationOutcome("name must match", "name must match"));
        var condition = new Condition(func, "bob", "alice", null, outcomes);
        return new AssertionResult(condition, "name must match", passed, severity);
    }

    private static AssertionResult passing() {
        return assertion("isNotNull", true, AssertionSeverity.FAIL);
    }

    private static AssertionResult failing() {
        return assertion("isEqualTo", false, AssertionSeverity.FAIL);
    }

    private static AssertionResult warning() {
        return assertion("isLessThan", false, AssertionSeverity.WARN);
    }

    private static RunEvent finished(RequestResult result) {
        return new RunEvent.RequestFinished(result);
    }

    private static RunEvent runFinished(long elapsedMs, boolean cancelled) {
        return new RunEvent.RunFinished(new RunResult(List.of(), List.of(), elapsedMs, cancelled, null));
    }

    private static void feed(RunListener listener, RunEvent... events) {
        for (var event : events) {
            listener.on(event);
        }
    }

    // --- the reporter ---

    @Test
    void constructor_throwsForANullStream() {
        // when / then
        assertThatThrownBy(() -> new ConsoleRunReporter(null)).isInstanceOf(BratException.class);
    }

    @Test
    void name_isConsole() {
        // when / then
        assertThat(underTest.name()).isEqualTo("console").isEqualTo(ConsoleRunReporter.NAME);
    }

    @Test
    void create_rejectsAnUnknownArgument() {
        // when / then - a typo fails before the run
        assertThatThrownBy(() -> listener(Map.of("detial", "all")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("detial");
    }

    @Test
    void create_rejectsAnUnknownDetailValue() {
        // when / then
        assertThatThrownBy(() -> listener(Map.of("detail", "verbose")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("verbose");
    }

    @Test
    void create_acceptsBothDetailValues() {
        // when / then
        assertThat(listener(Map.of("detail", "failures"))).isNotNull();
        assertThat(listener(Map.of("detail", "all"))).isNotNull();
    }

    @Test
    void create_returnsAFreshListenerOnEveryCall() {
        // when
        var first = listener();
        var second = listener();

        // then
        assertThat(first).isNotSameAs(second);
    }

    @Test
    void create_printsNothing() {
        // when
        listener();

        // then
        assertThat(printed()).isEmpty();
    }

    // --- run and suites ---

    @Test
    void on_printsNothingForRunStarted() {
        // when
        listener().on(new RunEvent.RunStarted(Instant.now()));

        // then
        assertThat(printed()).isEmpty();
    }

    @Test
    void on_printsASuiteNameIndentedByItsDepth() {
        // when
        feed(listener(), new RunEvent.SuiteEntered("root", "root"), new RunEvent.SuiteEntered("root/child", "child"));

        // then
        assertThat(lines()).containsExactly("root", "  child");
    }

    @Test
    void on_printsNothingWhenASuiteCompletes() {
        // when
        listener().on(new RunEvent.SuiteExited("root", new SuiteStatus.Completed(), 5));

        // then
        assertThat(printed()).isEmpty();
    }

    @Test
    void on_printsASuiteThatDidNotCompleteWithItsReason() {
        // when
        feed(
                listener(),
                new RunEvent.SuiteExited("root/admin", new SuiteStatus.Aborted("The setup request 'login' failed"), 5),
                new RunEvent.SuiteExited("root/beta", new SuiteStatus.Skipped("flag off"), 0),
                new RunEvent.SuiteExited("root/late", new SuiteStatus.Cancelled(), 0));

        // then
        assertThat(collapsed())
                .contains("ABORTED admin — The setup request 'login' failed")
                .contains("SKIPPED beta — flag off")
                .contains("CANCELLED late");
    }

    // --- requests ---

    @Test
    void on_printsAPassingRequestWithItsTimeIndentedUnderItsSuite() {
        // when
        listener().on(finished(result("users/create user", completed(), 45, passing())));

        // then
        assertThat(lines()).singleElement().satisfies(line -> {
            assertThat(line).startsWith("  PASS");
            assertThat(line.replaceAll(" +", " ")).isEqualTo(" PASS create user (45 ms)");
        });
    }

    @Test
    void on_tagsASetupOrTeardownRequest() {
        // given
        var setup = new RequestResult(coordinates("users/login", Phase.SETUP), sent(), null, completed(), 120, 0, null);
        var teardown =
                new RequestResult(coordinates("users/logout", Phase.TEARDOWN), sent(), null, completed(), 30, 0, null);

        // when
        feed(listener(), finished(setup), finished(teardown));

        // then
        assertThat(collapsed()).contains("PASS login [setup] (120 ms)").contains("PASS logout [teardown] (30 ms)");
    }

    @Test
    void on_printsASkippedRequestWithItsReasonAndNoTime() {
        // when
        listener().on(finished(result("users/delete", new RequestStatus.Skipped("Skipped due to condition x"), 0)));

        // then
        assertThat(collapsed().strip()).isEqualTo("SKIP delete — Skipped due to condition x");
    }

    @Test
    void on_printsAnErroredRequestWithItsMessage() {
        // when
        listener().on(finished(result("users/avatar", new RequestStatus.Errored("connection refused"), 3)));

        // then
        assertThat(collapsed()).contains("ERROR avatar (3 ms) — connection refused");
    }

    @Test
    void on_printsAGiveUpAsAFailureWithItsMessage() {
        // given
        var gaveUp = new RequestStatus.GaveUp(new RequestStatus.Completed(Map.of(), 10, 5), "condition never met");

        // when
        listener().on(finished(result("jobs/poll", gaveUp, 2100)));

        // then
        assertThat(collapsed()).contains("FAIL poll (2.1 s) — condition never met");
    }

    @Test
    void on_printsAFailedAssertionWithItsOutcomesAndTheRequestAsSent() {
        // when
        listener().on(finished(result("users/read user", completed(), 38, passing(), failing())));

        // then
        assertThat(collapsed())
                .contains("FAIL read user (38 ms)")
                .contains("FAIL isEqualTo — name must match")
                .contains("a: ${response.json.$.name} → bob")
                .contains("b: alice")
                .contains("HttpRequestDefinition:")
                .contains("url: ${env.baseUrl}/users/42 → http://h/users/42")
                .doesNotContain("isNotNull")
                .doesNotContain("message: ");
    }

    @Test
    void on_indentsTheDetailBelowItsRequest() {
        // when
        listener().on(finished(result("users/read user", completed(), 38, failing())));

        // then
        var requestLine = lines().getFirst();
        var requestIndent = requestLine.length() - requestLine.stripLeading().length();
        assertThat(lines().subList(1, lines().size()))
                .isNotEmpty()
                .allSatisfy(line ->
                        assertThat(line.length() - line.stripLeading().length()).isGreaterThan(requestIndent));
    }

    @Test
    void on_showsAFailedWarnEvenUnderAPassingRequest() {
        // when
        listener().on(finished(result("users/read user", completed(), 38, warning())));

        // then - a warning does not fail its request, and would otherwise be invisible
        assertThat(collapsed())
                .contains("PASS read user (38 ms)")
                .contains("WARN isLessThan — name must match")
                .doesNotContain("HttpRequestDefinition:");
    }

    @Test
    void on_showsNoDetailUnderAPassingRequestByDefault() {
        // when
        listener().on(finished(result("users/read user", completed(), 38, passing())));

        // then
        assertThat(lines()).hasSize(1);
    }

    @Test
    void on_showsEveryAssertionAndTheRequestAsSentWithDetailAll() {
        // when
        listener(Map.of("detail", "all")).on(finished(result("users/read user", completed(), 38, passing())));

        // then
        assertThat(collapsed())
                .contains("PASS read user (38 ms)")
                .contains("PASS isNotNull — name must match")
                .contains("url: ${env.baseUrl}/users/42 → http://h/users/42");
    }

    @Test
    void on_showsCaptureFailures() {
        // given
        var result = new RequestResult(
                coordinates("users/login", Phase.MAIN),
                sent(),
                null,
                completed(),
                20,
                0,
                new ResponseActionsResult(
                        List.of(), List.of(new CaptureFailure("token", "${response.json.$.token}", "no such path"))));

        // when
        listener().on(finished(result));

        // then
        assertThat(collapsed()).contains("FAIL login (20 ms)").contains("CAPTURE token — no such path");
    }

    @Test
    void on_showsTheRequestAsSentUnderAnErroredRequest() {
        // when
        listener().on(finished(result("users/avatar", new RequestStatus.Errored("connection refused"), 3)));

        // then
        assertThat(collapsed()).contains("url: ${env.baseUrl}/users/42 → http://h/users/42");
    }

    @Test
    void on_showsNoSentRequestWhenTheDefinitionWasNeverInterpolated() {
        // given - the definition itself could not be interpolated, so there is nothing to show
        var result = new RequestResult(
                coordinates("users/avatar", Phase.MAIN),
                authored(),
                null,
                new RequestStatus.Errored("no such variable"),
                1,
                0,
                null);

        // when
        listener().on(finished(result));

        // then
        assertThat(lines()).hasSize(1);
    }

    @Test
    void on_neverPrintsASecret() {
        // given - the sent header and a failed assertion both involve a secret
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("a", new InterpolationOutcome(SECRET, "${secrets.token} → ***", true));
        outcomes.put("b", new InterpolationOutcome("x", "x"));
        var overSecret = new AssertionResult(
                new Condition("isEqualTo", SECRET, "x", null, outcomes), "token", false, AssertionSeverity.FAIL);

        // when
        listener(Map.of("detail", "all")).on(finished(result("users/read", completed(), 5, overSecret)));

        // then
        assertThat(printed()).contains("***").doesNotContain(SECRET);
    }

    @Test
    void on_printsEachAttemptOfAPollingRequest() {
        // given
        var coordinates = coordinates("jobs/poll job", Phase.MAIN);

        // when
        feed(
                listener(),
                new AttemptFinished(coordinates, 1, 10, 12, false, null),
                new AttemptFinished(coordinates, 2, 10, 15, false, "connection reset"));

        // then
        assertThat(lines()).hasSize(2);
        assertThat(collapsed())
                .contains("poll job — attempt 1/10, condition not met (12 ms)")
                .contains("attempt 2/10")
                .contains("connection reset");
    }

    @Test
    void on_printsDurationsInMillisecondsBelowASecondAndSecondsFromOne() {
        // when
        feed(
                listener(),
                finished(result("s/fast", completed(), 999, passing())),
                finished(result("s/edge", completed(), 1000, passing())),
                finished(result("s/slow", completed(), 2149, passing())));

        // then
        assertThat(collapsed())
                .contains("fast (999 ms)")
                .contains("edge (1.0 s)")
                .contains("slow (2.1 s)");
    }

    @Test
    void on_printsAnAttemptThatMetItsCondition() {
        // when
        listener().on(new AttemptFinished(coordinates("jobs/poll job", Phase.MAIN), 3, 10, 9, true, null));

        // then
        assertThat(collapsed()).contains("poll job — attempt 3/10, condition met (9 ms)");
    }

    @Test
    void on_printsAnAssertionWithoutAMessageAsItsFuncAlone() {
        // given
        var outcomes = Map.of("a", new InterpolationOutcome("1", "1"));
        var noMessage = new AssertionResult(
                new Condition("isNull", "1", null, null, outcomes), null, false, AssertionSeverity.FAIL);

        // when
        listener().on(finished(result("s/r", completed(), 1, noMessage)));

        // then
        assertThat(lines()).anySatisfy(line -> assertThat(line.strip()).isEqualTo("FAIL  isNull"));
    }

    @Test
    void on_printsNoOutcomesForAnAssertionThatCouldNotBeInterpolated() {
        // given - the authored condition comes back when interpolation fails, with no outcomes
        var authored = new AssertionResult(
                new Condition("isEqualTo", "${vars.missing}", "x"),
                "Interpolating the assertion failed",
                false,
                AssertionSeverity.FAIL);
        var result = new RequestResult(
                coordinates("s/r", Phase.MAIN),
                authored(),
                null,
                completed(),
                1,
                0,
                new ResponseActionsResult(List.of(authored), List.of()));

        // when
        listener().on(finished(result));

        // then - the request line and the assertion header, nothing beneath
        assertThat(lines()).hasSize(2);
        assertThat(collapsed()).contains("FAIL isEqualTo — Interpolating the assertion failed");
    }

    @Test
    void on_showsNoSentRequestUnderASkippedRequestEvenWithDetailAll() {
        // when - a skipped request was never sent
        listener(Map.of("detail", "all"))
                .on(finished(result("users/delete", new RequestStatus.Skipped("flag off"), 0)));

        // then
        assertThat(lines()).hasSize(1);
    }

    @Test
    void on_showsNoSentRequestForADefinitionThatCarriesNoOutcomes() {
        // given - a definition that is not interpolated data at all
        RequestDefinition opaque = () -> "ftp";
        var result = new RequestResult(
                coordinates("s/r", Phase.MAIN), opaque, null, new RequestStatus.Errored("refused"), 1, 0, null);

        // when
        listener().on(finished(result));

        // then
        assertThat(lines()).hasSize(1);
    }

    @Test
    void on_summarisesCancelledSuitesAlone() {
        // when
        feed(
                listener(),
                new RunEvent.SuiteExited("root/late", new SuiteStatus.Cancelled(), 1),
                new RunEvent.SuiteExited("root", new SuiteStatus.Cancelled(), 1),
                runFinished(1, true));

        // then
        assertThat(lines())
                .filteredOn(line -> line.startsWith("suites"))
                .singleElement()
                .satisfies(line -> assertThat(line.replaceAll(" +", " ")).isEqualTo("suites 2 cancelled 2"));
    }

    // --- the summary ---

    @Test
    void on_summarisesRequestsCountingErroredAndGivenUpAsFailed() {
        // given
        var gaveUp = new RequestStatus.GaveUp(new RequestStatus.Completed(Map.of(), 3, 5), "never");

        // when
        feed(
                listener(),
                finished(result("s/ok", completed(), 1, passing())),
                finished(result("s/bad", completed(), 1, failing())),
                finished(result("s/err", new RequestStatus.Errored("down"), 1)),
                finished(result("s/poll", gaveUp, 1)),
                finished(result("s/skip", new RequestStatus.Skipped("flag"), 0)),
                runFinished(10, false));

        // then
        assertThat(collapsed()).contains("requests 5 passed 1 failed 3 skipped 1");
    }

    @Test
    void on_summarisesAssertionsOnePerResultWithWarningsApart() {
        // when
        feed(
                listener(),
                finished(result("s/a", completed(), 1, passing(), passing(), failing())),
                finished(result("s/b", completed(), 1, warning(), passing())),
                runFinished(10, false));

        // then
        assertThat(collapsed()).contains("assertions 5 passed 3 failed 1 warned 1");
    }

    @Test
    void on_summarisesCaptureFailuresOnlyWhenThereAreAny() {
        // given
        var withCaptureFailure = new RequestResult(
                coordinates("s/login", Phase.MAIN),
                sent(),
                null,
                completed(),
                1,
                0,
                new ResponseActionsResult(List.of(), List.of(new CaptureFailure("t", "${x}", "missing"))));

        // when
        var clean = listener();
        feed(clean, finished(result("s/ok", completed(), 1, passing())), runFinished(1, false));
        var withoutRow = printed();
        buffer.reset();
        feed(listener(), finished(withCaptureFailure), runFinished(1, false));

        // then
        assertThat(withoutRow).doesNotContain("captures");
        assertThat(collapsed()).contains("captures failed 1");
    }

    @Test
    void on_summarisesSuitesOnlyWhenSomeDidNotComplete() {
        // given
        var completed = new RunEvent.SuiteExited("root", new SuiteStatus.Completed(), 1);

        // when
        feed(listener(), completed, runFinished(1, false));
        var withoutRow = printed();
        buffer.reset();
        feed(
                listener(),
                new RunEvent.SuiteExited("root/a", new SuiteStatus.Aborted("setup failed"), 1),
                new RunEvent.SuiteExited("root/b", new SuiteStatus.Skipped("flag"), 1),
                completed,
                runFinished(1, false));

        // then - the zero category, cancelled, is left out
        assertThat(withoutRow).doesNotContain("suites");
        assertThat(collapsed()).contains("suites 3 aborted 1 skipped 1").doesNotContain("cancelled 0");
    }

    @Test
    void on_endsTheSummaryWithTheDurationAndPassed() {
        // when
        feed(listener(), finished(result("s/ok", completed(), 1, passing())), runFinished(2900, false));

        // then
        assertThat(lines().getLast()).isEqualTo("2.9 s · PASSED");
    }

    @Test
    void on_reportsFailedWhenARequestFailed() {
        // when
        feed(listener(), finished(result("s/bad", completed(), 1, failing())), runFinished(20, false));

        // then
        assertThat(lines().getLast()).isEqualTo("20 ms · FAILED");
    }

    @Test
    void on_reportsFailedWhenASuiteWasAbortedThoughNoRequestFailed() {
        // when
        feed(
                listener(),
                new RunEvent.SuiteExited("root", new SuiteStatus.Aborted("the secret is missing"), 1),
                runFinished(20, false));

        // then
        assertThat(lines().getLast()).isEqualTo("20 ms · FAILED");
    }

    @Test
    void on_reportsCancelledForACancelledRun() {
        // when
        feed(listener(), finished(result("s/bad", completed(), 1, failing())), runFinished(20, true));

        // then
        assertThat(lines().getLast()).isEqualTo("20 ms · CANCELLED");
    }

    @Test
    void on_reportsAbortedWithWhatEndedARunThatFailedStructurally() {
        // given - nothing failed before the failure, so the counts alone would say PASSED
        var aborted = new RunResult(List.of(), List.of(), 54, false, "There is no handler named 'proxy'");

        // when
        feed(listener(), finished(result("s/ok", completed(), 1, passing())), new RunEvent.RunFinished(aborted));

        // then
        assertThat(lines().getLast()).isEqualTo("54 ms · ABORTED — There is no handler named 'proxy'");
    }

    @Test
    void on_countsTheEventsItReceivedNotTheResultsTheRunHandsOver() {
        // given - a result list the listener never saw as events
        var unseen = new ArrayList<RequestResult>();
        unseen.add(result("s/never", completed(), 1, failing()));

        // when
        listener().on(new RunEvent.RunFinished(new RunResult(unseen, List.of(), 5, false, null)));

        // then
        assertThat(collapsed()).contains("requests 0").contains("5 ms · PASSED");
    }

    @Test
    void on_flushesAfterEveryEvent() {
        // given - a stream that only shows what was flushed
        var flushed = new ByteArrayOutputStream();
        var reporter = new ConsoleRunReporter(
                new PrintStream(new BufferedOutputStream(flushed, 8192), false, StandardCharsets.UTF_8));
        var listener = reporter.create(new ReporterContext(Map.of(), renderer));

        // when
        listener.on(new RunEvent.SuiteEntered("root", "root"));

        // then
        assertThat(flushed.toString(StandardCharsets.UTF_8)).contains("root");
    }
}
