package dev.pbroman.brat.core.rendering;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.Phase;
import dev.pbroman.brat.core.data.result.RequestCoordinates;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RequestStatus;
import dev.pbroman.brat.core.data.result.ResponseActionsResult;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseRendererTest {

    private static final RequestCoordinates COORDINATES =
            new RequestCoordinates("suite/login", null, "login", Phase.MAIN, 1);
    private static final HttpRequestDefinition HTTP = new HttpRequestDefinition("http://h/login", "POST", null, null);

    private final ValueMask mask = new ValueMask();

    // --- fixtures ---

    private static Map<String, Object> response(int status, Map<String, List<String>> headers, String body) {
        var caseInsensitive = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(headers);
        var vars = new HashMap<String, Object>();
        vars.put("statusCode", status);
        vars.put("headers", caseInsensitive);
        if (body != null) {
            vars.put("body", body);
        }
        return vars;
    }

    /** A JSON response, flattened as the HTTP handler does: the body, and the same text under {@code json}. */
    private static Map<String, Object> json(int status, String body) {
        var vars = response(status, Map.of("Content-Type", List.of("application/json")), body);
        vars.put("json", body);
        return vars;
    }

    private static RequestResult result(RequestDefinition sent, Map<String, Object> responseVars) {
        return new RequestResult(
                COORDINATES,
                sent,
                null,
                new RequestStatus.Completed(responseVars, 1, 10),
                10,
                0,
                ResponseActionsResult.NONE);
    }

    private List<String> lines(Map<String, Object> responseVars) {
        return ResponseRenderer.lines(result(HTTP, responseVars), mask, false);
    }

    // --- arguments ---

    @Test
    void lines_throwsForANullResult() {
        // when / then
        assertThatThrownBy(() -> ResponseRenderer.lines(null, mask, false)).isInstanceOf(BratException.class);
    }

    @Test
    void lines_throwsForANullMask() {
        // given
        var result = result(HTTP, json(200, "{}"));

        // when / then
        assertThatThrownBy(() -> ResponseRenderer.lines(result, null, false)).isInstanceOf(BratException.class);
    }

    @Test
    void lines_isEmptyForARequestWithNoResponse() {
        // given
        var errored = new RequestResult(
                COORDINATES, HTTP, null, new RequestStatus.Errored("connection refused"), 10, 0, null);

        // when / then
        assertThat(ResponseRenderer.lines(errored, mask, false)).isEmpty();
    }

    // --- HTTP ---

    @Test
    void lines_rendersTheStatusHeadersAndAPrettyPrintedJsonBody() {
        // when
        var lines = lines(json(401, "{\"error\":\"invalid_grant\"}"));

        // then
        assertThat(lines)
                .containsExactly(
                        "response: 401",
                        "  Content-Type: application/json",
                        "  body:",
                        "    {",
                        "      \"error\" : \"invalid_grant\"",
                        "    }");
    }

    @Test
    void lines_printsAHeaderOncePerValueInTheNamespacesOrder() {
        // given
        var headers = Map.of(
                "set-cookie", List.of("A=1", "B=2"),
                "Content-Type", List.of("text/plain"),
                "X-Trace", List.of("abc"));

        // when
        var lines = lines(response(200, headers, null));

        // then - alphabetical ignoring case, as the namespace holds them
        assertThat(lines)
                .containsExactly(
                        "response: 200",
                        "  Content-Type: text/plain",
                        "  set-cookie: A=1",
                        "  set-cookie: B=2",
                        "  X-Trace: abc");
    }

    @Test
    void lines_showsATextBodyAsReceived() {
        // given
        var vars = response(500, Map.of("Content-Type", List.of("text/plain; charset=UTF-8")), "boom\n  at line 2");

        // when
        var lines = lines(vars);

        // then
        assertThat(lines).containsSubsequence("  body:", "    boom", "      at line 2");
    }

    @Test
    void lines_showsABodyThatIsNotAJsonDocumentAsReceived() {
        // given - declared JSON, but the namespace holds no json entry: a scalar, or text that does not parse
        var vars = response(200, Map.of("Content-Type", List.of("application/json")), "42");

        // when
        var lines = lines(vars);

        // then
        assertThat(lines).endsWith("  body:", "    42");
    }

    @Test
    void lines_treatsAMissingContentTypeAsText() {
        // when
        var lines = lines(response(200, Map.of(), "plain"));

        // then
        assertThat(lines).containsExactly("response: 200", "  body:", "    plain");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "text/html",
                "application/problem+json",
                "application/atom+xml",
                "application/xml",
                "application/javascript",
                "application/x-www-form-urlencoded",
                "TEXT/CSV; charset=UTF-8"
            })
    void lines_showsEveryTextContentTypeAsText(String contentType) {
        // when
        var lines = lines(response(200, Map.of("Content-Type", List.of(contentType)), "readable"));

        // then
        assertThat(lines).endsWith("  body:", "    readable");
    }

    @Test
    void lines_describesABinaryBodyWithItsSize() {
        // given
        var headers = Map.of("Content-Type", List.of("image/png"), "Content-Length", List.of("5120"));

        // when
        var lines = lines(response(200, headers, "�PNG�"));

        // then
        assertThat(lines).endsWith("  body: ‹binary body, image/png, 5120 bytes›");
    }

    @Test
    void lines_describesABinaryBodyWithoutASizeWhenNoneIsKnown() {
        // given
        var headers = Map.of("Content-Type", List.of("application/octet-stream"), "Content-Length", List.of("many"));

        // when
        var lines = lines(response(200, headers, "��"));

        // then
        assertThat(lines).endsWith("  body: ‹binary body, application/octet-stream›");
    }

    @Test
    void lines_describesABinaryBodyWithoutASizeWhenThereIsNoContentLength() {
        // when
        var lines = lines(response(200, Map.of("Content-Type", List.of("image/png")), "\uFFFDPNG"));

        // then
        assertThat(lines).endsWith("  body: ‹binary body, image/png›");
    }

    @Test
    void lines_printsNoBodyLineForAnEmptyBody() {
        // when
        var lines = lines(response(204, Map.of(), ""));

        // then
        assertThat(lines).containsExactly("response: 204");
    }

    @Test
    void lines_masksHeaderValuesAndTheBody() {
        // given
        mask.addGuessed("eyJhbGciOi");
        var vars = json(200, "{\"access_token\":\"eyJhbGciOi\"}");
        vars.put(
                "headers",
                new TreeMap<>(Map.of(
                        "Content-Type", List.of("application/json"), "Set-Cookie", List.of("SESSION=eyJhbGciOi"))));

        // when
        var lines = lines(vars);

        // then
        assertThat(lines).contains("  Set-Cookie: SESSION=***", "      \"access_token\" : \"***\"");
        assertThat(String.join("\n", lines)).doesNotContain("eyJhbGciOi");
    }

    @Test
    void lines_showsGuessedValuesWhenAskedTo() {
        // given
        mask.addGuessed("eyJhbGciOi");

        // when
        var lines = ResponseRenderer.lines(result(HTTP, json(200, "{\"access_token\":\"eyJhbGciOi\"}")), mask, true);

        // then
        assertThat(lines).contains("      \"access_token\" : \"eyJhbGciOi\"");
    }

    @Test
    void lines_cutsALongBodyAfterPrettyPrinting() {
        // given
        var body = "x".repeat(ReportText.MAX_LENGTH + 10);

        // when
        var lines = lines(response(200, Map.of("Content-Type", List.of("text/plain")), body));

        // then
        assertThat(lines).endsWith("    " + "x".repeat(ReportText.MAX_LENGTH), "    … 10 more characters");
    }

    @Test
    void lines_rendersAGivenUpPollsLastResponse() {
        // given
        var lastAttempt = new RequestStatus.Completed(json(202, "{\"status\":\"RUNNING\"}"), 3, 10);
        var result = new RequestResult(
                COORDINATES, HTTP, null, new RequestStatus.GaveUp(lastAttempt, "never COMPLETE"), 10, 0, null);

        // when
        var lines = ResponseRenderer.lines(result, mask, false);

        // then
        assertThat(lines).startsWith("response: 202");
    }

    // --- other protocols ---

    @Test
    void lines_rendersAnotherProtocolsResponseAsSortedEntries() {
        // given
        RequestDefinition ftp = () -> "ftp";
        var vars = Map.<String, Object>of("reply", "226 Transfer complete", "code", 226);

        // when
        var lines = ResponseRenderer.lines(result(ftp, vars), mask, false);

        // then
        assertThat(lines).containsExactly("response:", "  code: 226", "  reply: 226 Transfer complete");
    }

    // --- redirectNotice ---

    @Test
    void redirectNotice_namesARefusedRedirect() {
        // given
        var vars = response(302, Map.of(), null);
        vars.put("redirectNotFollowed", "https://login.other.example/x");

        // when
        var notice = ResponseRenderer.redirectNotice(result(HTTP, vars), mask, false);

        // then
        assertThat(notice)
                .contains("REDIRECT https://login.other.example/x not followed — another origin, and the request"
                        + " carries credentials");
    }

    @Test
    void redirectNotice_masksTheLocation() {
        // given
        mask.addGuessed("tok-12345");
        var vars = response(302, Map.of(), null);
        vars.put("redirectNotFollowed", "https://other.example/x?token=tok-12345");

        // when
        var notice = ResponseRenderer.redirectNotice(result(HTTP, vars), mask, false);

        // then
        assertThat(notice)
                .hasValueSatisfying(
                        line -> assertThat(line).contains("token=***").doesNotContain("tok-12345"));
    }

    @Test
    void redirectNotice_isEmptyWhenNothingWasRefusedOrThereIsNoResponse() {
        // given
        var errored = new RequestResult(
                COORDINATES, HTTP, null, new RequestStatus.Errored("connection refused"), 10, 0, null);

        // when / then
        assertThat(ResponseRenderer.redirectNotice(result(HTTP, response(302, Map.of(), null)), mask, false))
                .isEmpty();
        assertThat(ResponseRenderer.redirectNotice(errored, mask, false)).isEmpty();
    }

    @Test
    void redirectNotice_throwsForANullResultOrMask() {
        // when / then
        assertThatThrownBy(() -> ResponseRenderer.redirectNotice(null, mask, false))
                .isInstanceOf(BratException.class);
        assertThatThrownBy(() ->
                        ResponseRenderer.redirectNotice(result(HTTP, response(200, Map.of(), null)), null, false))
                .isInstanceOf(BratException.class);
    }
}
