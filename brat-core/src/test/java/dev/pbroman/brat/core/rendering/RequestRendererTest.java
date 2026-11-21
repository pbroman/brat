package dev.pbroman.brat.core.rendering;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ReportDetail;
import dev.pbroman.brat.core.api.reporting.ValueMask;
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
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.rendering.rules.ConsoleOutcomeRendererRule;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.REDIRECT_NOT_FOLLOWED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestRendererTest {

    private static final String SECRET = "s3cr3t";

    private final OutcomeRendererRuleDispatcher renderer =
            new OutcomeRendererRuleDispatcher(List.of(new ConsoleOutcomeRendererRule()));

    private final ValueMask mask = new ValueMask();

    // --- fixtures ---

    private static ReportDetail detail(Map<String, String> args) {
        return ReportDetail.of(args);
    }

    private static ReportDetail all() {
        return detail(
                Map.of(ReportDetail.ASSERTIONS, "all", ReportDetail.REQUEST, "all", ReportDetail.RESPONSE, "all"));
    }

    private static HttpRequestDefinition sent() {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put(
                "url", new InterpolationOutcome("http://h/users/42", "${env.baseUrl}/users/42 → http://h/users/42"));
        outcomes.put(
                "header.Authorization",
                new InterpolationOutcome("Bearer " + SECRET, "Bearer ${secrets.token} → Bearer " + SECRET, true));
        return new HttpRequestDefinition(
                "http://h/users/42", "GET", null, Map.of("Authorization", "Bearer " + SECRET), null, outcomes);
    }

    private static RequestStatus completed(Map<String, Object> extra) {
        var headers = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        headers.put("Content-Type", List.of("application/json"));
        var vars = new LinkedHashMap<String, Object>();
        vars.put("statusCode", 200);
        vars.put("headers", headers);
        vars.put("body", "{\"name\":\"bob\"}");
        vars.put("json", "{\"name\":\"bob\"}");
        vars.putAll(extra);
        return new RequestStatus.Completed(vars, 1, 10);
    }

    private static RequestResult result(
            RequestStatus status, List<CaptureFailure> captures, AssertionResult... assertions) {
        return new RequestResult(
                new RequestCoordinates("orders/read", null, "read", Phase.MAIN, 1),
                sent(),
                null,
                status,
                10,
                0,
                new ResponseActionsResult(List.of(assertions), captures));
    }

    private static RequestResult result(AssertionResult... assertions) {
        return result(completed(Map.of()), List.of(), assertions);
    }

    private static AssertionResult assertion(String func, boolean passed, AssertionSeverity severity, String message) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("a", new InterpolationOutcome("bob", "${response.json.$.name} → bob"));
        outcomes.put("b", new InterpolationOutcome("alice", "alice"));
        if (message != null) {
            outcomes.put("message", new InterpolationOutcome(message, message));
        }
        var condition = new Condition(func, "bob", "alice", null, outcomes);
        return new AssertionResult(condition, message, passed, severity);
    }

    private static AssertionResult failing() {
        return assertion("isEqualTo", false, AssertionSeverity.FAIL, "name must match");
    }

    private List<String> lines(RequestResult result, ReportDetail detail) {
        return RequestRenderer.lines(result, detail, mask, renderer);
    }

    // --- headline ---

    private static RequestResult withStatus(RequestStatus status, Phase phase, AssertionResult... assertions) {
        return new RequestResult(
                new RequestCoordinates("orders/read", null, "read", phase, 1),
                sent(),
                null,
                status,
                1500,
                0,
                new ResponseActionsResult(List.of(assertions), List.of()));
    }

    @Test
    void headline_isPassThenTheNameAndTheDuration() {
        // when
        var line = RequestRenderer.headline(
                withStatus(new RequestStatus.Completed(Map.of(), 1, 12), Phase.MAIN), mask, false);

        // then
        assertThat(line).isEqualTo("PASS   read (1.5 s)");
    }

    @Test
    void headline_isFailForAFailedAssertion() {
        // when
        var line = RequestRenderer.headline(
                withStatus(new RequestStatus.Completed(Map.of(), 1, 12), Phase.MAIN, failing()), mask, false);

        // then
        assertThat(line).isEqualTo("FAIL   read (1.5 s)");
    }

    @Test
    void headline_isErrorWithTheErrorsMessage() {
        // when
        var line = RequestRenderer.headline(
                withStatus(new RequestStatus.Errored("No 'baseUrl' in env"), Phase.MAIN), mask, false);

        // then
        assertThat(line).isEqualTo("ERROR  read (1.5 s) — No 'baseUrl' in env");
    }

    @Test
    void headline_isFailWithTheMessageOfAGiveUp() {
        // given
        var gaveUp = new RequestStatus.GaveUp(new RequestStatus.Completed(Map.of(), 3, 12), "never became ready");

        // when
        var line = RequestRenderer.headline(withStatus(gaveUp, Phase.MAIN), mask, false);

        // then
        assertThat(line).isEqualTo("FAIL   read (1.5 s) — never became ready");
    }

    @Test
    void headline_isSkipWithTheReasonAndNoDuration() {
        // when
        var line = RequestRenderer.headline(
                withStatus(new RequestStatus.Skipped("tenant-only checks"), Phase.MAIN), mask, false);

        // then
        assertThat(line).isEqualTo("SKIP   read — tenant-only checks");
    }

    @Test
    void headline_tagsSetupAndTeardown() {
        // when
        var setup = RequestRenderer.headline(
                withStatus(new RequestStatus.Completed(Map.of(), 1, 12), Phase.SETUP), mask, false);
        var teardown = RequestRenderer.headline(
                withStatus(new RequestStatus.Completed(Map.of(), 1, 12), Phase.TEARDOWN), mask, false);

        // then
        assertThat(setup).isEqualTo("PASS   read [setup] (1.5 s)");
        assertThat(teardown).isEqualTo("PASS   read [teardown] (1.5 s)");
    }

    @Test
    void headline_masksTheReason() {
        // given
        mask.addSecret(SECRET);

        // when
        var line = RequestRenderer.headline(
                withStatus(new RequestStatus.Errored("rejected " + SECRET), Phase.MAIN), mask, false);

        // then
        assertThat(line).doesNotContain(SECRET).endsWith("rejected ***");
    }

    @Test
    void headline_refusesANullResultOrMask() {
        // when / then
        assertThatThrownBy(() -> RequestRenderer.headline(null, mask, false)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> RequestRenderer.headline(result(failing()), null, false))
                .isInstanceOf(BratException.class);
    }

    // --- what is shown ---

    @Test
    void lines_isEmptyForAPassedRequestUnderTheDefaultDetail() {
        // given
        var passed = result(assertion("isNotNull", true, AssertionSeverity.FAIL, null));

        // when
        var lines = lines(passed, detail(Map.of()));

        // then
        assertThat(lines).isEmpty();
    }

    @Test
    void lines_showAFailedAssertionWithItsMessageAndItsOutcomesIndented() {
        // when
        var lines =
                lines(result(failing()), detail(Map.of(ReportDetail.REQUEST, "none", ReportDetail.RESPONSE, "none")));

        // then
        assertThat(lines.getFirst()).isEqualTo("FAIL  isEqualTo — name must match");
        assertThat(lines.subList(1, lines.size()))
                .isNotEmpty()
                .allSatisfy(line -> assertThat(line).startsWith("  "))
                .anySatisfy(line -> assertThat(line).contains("${response.json.$.name} → bob"))
                .noneSatisfy(line -> assertThat(line).contains("message"));
    }

    @Test
    void lines_markAPassedAssertionPassAndAFailedWarnAssertionWarn() {
        // given
        var passed = assertion("isNotNull", true, AssertionSeverity.FAIL, null);
        var warned = assertion("isLessThan", false, AssertionSeverity.WARN, null);
        var only = detail(
                Map.of(ReportDetail.ASSERTIONS, "all", ReportDetail.REQUEST, "none", ReportDetail.RESPONSE, "none"));

        // when
        var lines = lines(result(passed, warned), only);

        // then
        assertThat(lines).contains("PASS  isNotNull", "WARN  isLessThan");
        assertThat(lines.indexOf("PASS  isNotNull")).isLessThan(lines.indexOf("WARN  isLessThan"));
    }

    @Test
    void lines_leaveOutAnAssertionTheDetailDoesNotShow() {
        // given
        var passed = assertion("isNotNull", true, AssertionSeverity.FAIL, null);

        // when
        var lines = lines(result(passed, failing()), detail(Map.of(ReportDetail.REQUEST, "none")));

        // then
        assertThat(lines).noneSatisfy(line -> assertThat(line).contains("isNotNull"));
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith("FAIL  isEqualTo"));
    }

    @Test
    void lines_showEveryCaptureFailure() {
        // given
        var captures = List.of(
                new CaptureFailure("orderId", "${response.json.$.id}", "no id in the response"),
                new CaptureFailure("etag", "${response.headers.ETag}", "no ETag header"));

        // when
        var lines = lines(result(completed(Map.of()), captures), detail(Map.of(ReportDetail.REQUEST, "none")));

        // then
        assertThat(lines)
                .containsSubsequence("CAPTURE orderId — no id in the response", "CAPTURE etag — no ETag header");
    }

    @Test
    void lines_showTheRequestAsSentUnderARequestLine() {
        // when
        var lines = lines(result(failing()), detail(Map.of(ReportDetail.RESPONSE, "none")));

        // then
        var at = lines.indexOf("request:");
        assertThat(at).isPositive();
        assertThat(lines.get(at + 1)).startsWith("  ");
        assertThat(lines.subList(at + 1, lines.size()))
                .anySatisfy(line -> assertThat(line).contains("http://h/users/42"));
    }

    @Test
    void lines_showTheResponseAsResponseRendererRendersIt() {
        // given
        var failed = result(failing());
        var detail = detail(Map.of(ReportDetail.REQUEST, "none"));

        // when
        var lines = lines(failed, detail);

        // then
        assertThat(lines).endsWith(ResponseRenderer.lines(failed, mask, false).toArray(String[]::new));
    }

    @Test
    void lines_startWithTheNoticeOfARedirectNotFollowed() {
        // given
        var redirected = result(
                completed(Map.of(REDIRECT_NOT_FOLLOWED, "https://elsewhere.example.com/login")), List.of(), failing());

        // when
        var lines = lines(redirected, all());

        // then
        assertThat(lines.getFirst())
                .isEqualTo(
                        ResponseRenderer.redirectNotice(redirected, mask, false).orElseThrow());
    }

    @Test
    void lines_keepTheOrderNoticeAssertionsCapturesRequestResponse() {
        // given
        var redirected = result(
                completed(Map.of(REDIRECT_NOT_FOLLOWED, "https://elsewhere.example.com/login")),
                List.of(new CaptureFailure("orderId", "${response.json.$.id}", "no id")),
                failing());

        // when
        var lines = lines(redirected, all());

        // then
        assertThat(lines.get(0)).startsWith("REDIRECT");
        var assertionAt = lines.indexOf("FAIL  isEqualTo — name must match");
        var captureAt = lines.indexOf("CAPTURE orderId — no id");
        var requestAt = lines.indexOf("request:");
        var responseAt = lines.indexOf("response: 200");
        assertThat(List.of(assertionAt, captureAt, requestAt, responseAt))
                .isSorted()
                .doesNotContain(-1);
    }

    // --- masking ---

    @Test
    void lines_maskEveryValueTakenFromTheRun() {
        // given
        mask.addSecret(SECRET);
        var leaky = assertion("isEqualTo", false, AssertionSeverity.FAIL, "token was " + SECRET);
        var captures = List.of(new CaptureFailure("token", "${response.json.$.t}", "saw " + SECRET));

        // when
        var lines = lines(result(completed(Map.of()), captures, leaky), all());

        // then
        assertThat(String.join("\n", lines)).doesNotContain(SECRET).contains("***");
    }

    @Test
    void lines_cutAValueLongerThanReportTextAllows() {
        // given
        var longMessage = "x".repeat(ReportText.MAX_LENGTH + 10);
        var verbose = assertion("isEqualTo", false, AssertionSeverity.FAIL, longMessage);

        // when
        var lines = lines(result(verbose), detail(Map.of(ReportDetail.REQUEST, "none", ReportDetail.RESPONSE, "none")));

        // then
        assertThat(String.join("\n", lines)).contains("10 more characters").doesNotContain(longMessage);
    }

    // --- arguments ---

    @Test
    void lines_refuseANullResult() {
        // when / then
        assertThatThrownBy(() -> RequestRenderer.lines(null, all(), mask, renderer))
                .isInstanceOf(BratException.class);
    }

    @Test
    void lines_refuseANullDetail() {
        // when / then
        assertThatThrownBy(() -> RequestRenderer.lines(result(failing()), null, mask, renderer))
                .isInstanceOf(BratException.class);
    }

    @Test
    void lines_refuseANullMask() {
        // when / then
        assertThatThrownBy(() -> RequestRenderer.lines(result(failing()), all(), null, renderer))
                .isInstanceOf(BratException.class);
    }

    @Test
    void lines_refuseANullRenderer() {
        // when / then
        assertThatThrownBy(() -> RequestRenderer.lines(result(failing()), all(), mask, null))
                .isInstanceOf(BratException.class);
    }
}
