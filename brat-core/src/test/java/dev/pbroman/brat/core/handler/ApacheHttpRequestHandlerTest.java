package dev.pbroman.brat.core.handler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;
import static dev.pbroman.brat.core.util.Constants.RAW_BODY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApacheHttpRequestHandlerTest {

    private StubHttpServer stub;
    private ApacheHttpRequestHandler underTest;

    @BeforeEach
    void setUp() {
        stub = new StubHttpServer();
        underTest = new ApacheHttpRequestHandler();
    }

    @AfterEach
    void tearDown() {
        underTest.close();
        stub.close();
    }

    // ---------- the happy path ----------

    @Test
    void performRequest_returnsTheStatusHeadersAndBody() {
        // given
        stub.respond(200, "{\"id\": \"1\"}", "Content-Type", "application/json");

        // when
        var response = underTest.performRequest(get("/orders"));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"id\": \"1\"}");
        assertThat(response.headers().get("content-type")).containsExactly("application/json");
    }

    @Test
    void performRequest_sendsTheMethodAndPath() {
        // given
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "DELETE", null, null, null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().method()).isEqualTo("DELETE");
        assertThat(stub.lastRequest().path()).isEqualTo("/orders");
    }

    @Test
    void performRequest_upperCasesTheMethod() {
        // given - a spelling, not a different method; HTTP methods are case-sensitive tokens and
        // every standard one is upper case
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "post", null, null, null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().method()).isEqualTo("POST");
    }

    @Test
    void performRequest_defaultsToGet() {
        // given - the definition applies the default, so the handler must not override it
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", null, null, null, null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().method()).isEqualTo("GET");
    }

    @Test
    void performRequest_sendsTheDeclaredHeaders() {
        // given
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "GET", null, null, Map.of("X-Trace", "abc"), null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().headers()).containsKey("X-trace");
    }

    @Test
    void performRequest_returnsEveryValueOfARepeatedHeader() {
        // given - the fidelity the responseVars namespace deliberately drops
        stub.respond(200, null, "Set-Cookie", "a=1", "Set-Cookie", "b=2");

        // when
        var response = underTest.performRequest(get("/orders"));

        // then
        assertThat(response.headers().get("Set-Cookie")).containsExactly("a=1", "b=2");
    }

    @Test
    void performRequest_returnsANullBodyWhenThereIsNone() {
        // given
        stub.respond(204, null);

        // when
        var response = underTest.performRequest(get("/orders"));

        // then
        assertThat(response.body()).isNull();
    }

    // ---------- a response is a response, whatever it says ----------

    @Test
    void performRequest_returnsAClientErrorRatherThanThrowing() {
        // given
        stub.respond(404, "not found");

        // when
        var response = underTest.performRequest(get("/orders"));

        // then - a suite asserting on a 404 is an ordinary suite
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEqualTo("not found");
    }

    @Test
    void performRequest_returnsAGatewayErrorRatherThanThrowing() {
        // given - 502/503/504 look like infrastructure noise and are still something a server said;
        // throwing would discard the proxy's diagnostic body and stop assertions from running
        stub.respond(502, "upstream is down");

        // when
        var response = underTest.performRequest(get("/orders"));

        // then
        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.body()).isEqualTo("upstream is down");
    }

    @Test
    void performRequest_returnsAServiceUnavailableRatherThanThrowing() {
        // given - indistinguishable from an application in maintenance mode, which a suite may test
        stub.respond(503, "maintenance");

        // when / then
        assertThat(underTest.performRequest(get("/orders")).statusCode()).isEqualTo(503);
    }

    // ---------- bodies ----------

    @Test
    void performRequest_sendsARawBody() {
        // given
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "POST", null, Map.of(RAW_BODY, "{\"a\": 1}"), null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().body()).isEqualTo("{\"a\": 1}");
    }

    @Test
    void performRequest_sendsABodyOnAnyMethodThatDeclaresOne() {
        // given - restricting bodies to POST/PUT/PATCH would silently drop what an author wrote
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "DELETE", null, Map.of(RAW_BODY, "reason=cleanup"), null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().body()).isEqualTo("reason=cleanup");
    }

    @Test
    void performRequest_sendsAFileBodyFromBodyStringWithoutReadingIt(@TempDir Path dir) throws Exception {
        // given - the interpolator resolved the file already; the handler reads one key and no disk.
        // The file exists and holds something else entirely, so a handler that read it would fail here.
        var file = dir.resolve("payload.json");
        Files.writeString(file, "{\"from\": \"the file on disk\"}");
        var body = new LinkedHashMap<String, String>();
        body.put(FILE_BODY, "file:" + file);
        body.put(BODY_STRING, "{\"from\": \"the interpolator\"}");
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "POST", null, body, null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().body()).isEqualTo("{\"from\": \"the interpolator\"}");
    }

    @Test
    void performRequest_throwsWhenAFileBodyWasNeverResolved(@TempDir Path dir) throws Exception {
        // given - a file entry with no _bodyString means nothing resolved it, which is a wiring fault
        // rather than an authoring one. The file deliberately **exists**: a handler that still reads
        // from disk would succeed here, so this fails until the read leaves the handler.
        var file = dir.resolve("payload.json");
        Files.writeString(file, "{\"from\": \"the file on disk\"}");
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "POST", null, Map.of(FILE_BODY, "file:" + file), null, null);

        // then
        assertThatThrownBy(() -> underTest.performRequest(definition)).isInstanceOf(BratException.class);
    }

    @Test
    void performRequest_sendsFormFieldsJoinedUnderAFormContentType() {
        // given - a LinkedHashMap, because the payload is joined in the body's iteration order and
        // Map.of has none. The definition derives _bodyString; this asserts the handler sends it
        var body = new LinkedHashMap<String, String>();
        body.put("field", "value");
        body.put("other", "second");
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders",
                "POST",
                null,
                body,
                Map.of("Content-Type", "application/x-www-form-urlencoded"),
                null);

        // when
        underTest.performRequest(definition);

        // then - the one path where the payload is neither authored nor read from a file
        assertThat(stub.lastRequest().body()).isEqualTo("field=value&other=second");
        assertThat(stub.lastRequest().headers().get("Content-type"))
                .containsExactly("application/x-www-form-urlencoded");
    }

    @Test
    void performRequest_throwsWhenABodyHoldsNothingToSend() {
        // given - form fields with no form Content-Type to join them under, so nothing was derived
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "POST", null, Map.of("field", "value"), null, null);

        // then - sending an empty body would let a broken request pass green
        assertThatThrownBy(() -> underTest.performRequest(definition)).isInstanceOf(BratException.class);
    }

    @Test
    void performRequest_sendsExactlyOneContentTypeWhenTheAuthorDeclaredOne() {
        // given - the combination the entity construction is designed around: an entity supplying its
        // own content type would contribute a second header beside the one written here
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders",
                "POST",
                null,
                Map.of(RAW_BODY, "{\"a\": 1}"),
                Map.of("Content-Type", "application/json"),
                null);

        // when
        underTest.performRequest(definition);

        // then - exactly one, and the author's value; the JDK server normalises the name's case
        assertThat(stub.lastRequest().headers().get("Content-type")).containsExactly("application/json");
    }

    @Test
    void performRequest_sendsABodyWithNoContentTypeWhenNoneWasDeclared() {
        // given - nothing is invented on the author's behalf
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "POST", null, Map.of(RAW_BODY, "plain"), null, null);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().headers()).doesNotContainKey("Content-type");
    }

    @Test
    void performRequest_throwsWhenTheContentTypeNamesAnUnknownCharset() {
        // given - parses as a media type, then fails on the charset, which is its own branch
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders",
                "POST",
                null,
                Map.of(RAW_BODY, "x"),
                Map.of("Content-Type", "text/plain; charset=NOPE"),
                null);

        // then
        assertThatThrownBy(() -> underTest.performRequest(definition))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("charset");
    }

    @Test
    void performRequest_sendsNoBodyWhenNoneIsDeclared() {
        // when
        underTest.performRequest(get("/orders"));

        // then
        assertThat(stub.lastRequest().body()).isEmpty();
    }

    // ---------- timeout ----------

    @Test
    void performRequest_throwsWhenTheTimeoutIsNotANumber() {
        // given - a typo, or an interpolated value that resolved to nonsense
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "GET", "abc", null, null, null);

        // then - proceeding without it would blame the server for a hang the suite caused
        assertThatThrownBy(() -> underTest.performRequest(definition))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("timeout");
    }

    @Test
    void performRequest_throwsWhenTheTimeoutIsNotPositive() {
        // given - HttpClient5 reads zero as infinite, so accepting it would invert what was asked for
        // and turn a tight ceiling into no ceiling at all
        for (var timeout : new String[] {"0", "-1"}) {
            var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "GET", timeout, null, null, null);

            // then
            assertThatThrownBy(() -> underTest.performRequest(definition))
                    .as("timeout %s", timeout)
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("positive");
        }
    }

    @Test
    void performRequest_throwsWhenTheTimeoutElapses() {
        // given - our timeout, which unlike their 504 produces no response at all
        stub.respond(200, "too late");
        stub.respondSlowly(2000);
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "GET", "200", null, null, null);

        // then
        assertThatThrownBy(() -> underTest.performRequest(definition)).isInstanceOf(BratException.class);
    }

    @Test
    void performRequest_appliesTheDefaultTimeoutWhenNoneIsDeclared() {
        // given - the default the suite-author doc publishes, not "no ceiling at all"
        stub.respond(200, "ok");

        // then - a fast response is unaffected by it
        assertThatCode(() -> underTest.performRequest(get("/orders"))).doesNotThrowAnyException();
    }

    // ---------- transport failure ----------

    @Test
    void performRequest_throwsWhenTheHostRefusesTheConnection() {
        // given - nothing is listening here; there is no response to record
        var definition = new HttpRequestDefinition("http://localhost:1/orders", "GET", null, null, null, null);

        // then
        assertThatThrownBy(() -> underTest.performRequest(definition)).isInstanceOf(BratException.class);
    }

    @Test
    void performRequest_throwsForAMalformedUrl() {
        // given
        var definition = new HttpRequestDefinition("not a url", "GET", null, null, null, null);

        // then
        assertThatThrownBy(() -> underTest.performRequest(definition)).isInstanceOf(BratException.class);
    }

    @Test
    void performRequest_throwsWhenTheUrlIsBlank() {
        // given - the likelier authoring mistake of the two: an unset ${env.baseUrl} resolving to
        // nothing. Without this, new URI("") yields a relative URI that fails deep inside the client
        for (var url : new String[] {null, "", "   "}) {
            var definition = new HttpRequestDefinition(url, "GET", null, null, null, null);

            // then
            assertThatThrownBy(() -> underTest.performRequest(definition))
                    .as("url %s", url == null ? "<null>" : "'" + url + "'")
                    .isInstanceOf(BratException.class)
                    .hasMessageContaining("url");
        }
    }

    @Test
    void performRequest_throwsForANullDefinition() {
        assertThatThrownBy(() -> underTest.performRequest(null)).isInstanceOf(BratException.class);
    }

    // ---------- what this handler does not do yet ----------

    @Test
    void performRequest_ignoresDeclaredAuth() {
        // given - auth binds and interpolates and is inert; pinned so the day it stops being
        // inert is a failing test rather than a surprise
        var auth = new Auth("basic", "u", "p");
        var definition = new HttpRequestDefinition(stub.baseUrl() + "/orders", "GET", null, null, null, auth);

        // when
        underTest.performRequest(definition);

        // then
        assertThat(stub.lastRequest().headers()).doesNotContainKey("Authorization");
    }

    @Test
    void performRequest_sendsOneRequestWhenTheServerAnswers503() {
        // given - the client would retry this once, a second later, and the result would record one
        // attempt while the server saw two
        stub.respond(503, "unavailable");

        // when
        var response = underTest.performRequest(get("/orders"));

        // then
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(stub.requestCount()).isEqualTo(1);
    }

    @Test
    void performRequest_sendsOneRequestWhenTheServerAnswers429() {
        // given - the other status in the client's retriable set, and the one a rate-limiting suite
        // is deliberately provoking
        stub.respond(429, "slow down");

        // when
        var response = underTest.performRequest(get("/orders"));

        // then
        assertThat(response.statusCode()).isEqualTo(429);
        assertThat(stub.requestCount()).isEqualTo(1);
    }

    @Test
    void performRequest_neverReplaysACookieTheServerSet() {
        // given - the client would manage cookies by default, in a store belonging to the client and
        // therefore to the process, so a session would outlive the run that established it
        stub.respond(200, "ok", "Set-Cookie", "session=abc123; Path=/");
        underTest.performRequest(get("/login"));

        // when
        underTest.performRequest(get("/orders"));

        // then
        assertThat(stub.lastRequest().headers()).doesNotContainKey("Cookie");
    }

    @Test
    void performRequest_returnsASetCookieHeaderLikeAnyOther() {
        // given - not sending cookies is not the same as hiding them: a suite captures what it needs
        stub.respond(200, "ok", "Set-Cookie", "session=abc123; Path=/", "Set-Cookie", "theme=dark");

        // when
        var response = underTest.performRequest(get("/login"));

        // then
        assertThat(response.headers().get("set-cookie")).containsExactly("session=abc123; Path=/", "theme=dark");
    }

    // ---------- the registration key, and the protocol's own vocabulary ----------

    @Test
    void protocol_isHttp() {
        // then - the half of the key a request is dispatched on, and what a requestHandlers map keys by
        assertThat(underTest.protocol()).isEqualTo("http");
    }

    @Test
    void name_isTheNameASuiteSelectsItBy() {
        // then - core makes this one the default for http, so the string is load-bearing
        assertThat(underTest.name()).isEqualTo("httpclient5");
        assertThat(ApacheHttpRequestHandler.NAME).isEqualTo(underTest.name());
    }

    @Test
    void definitionType_isWhatAnAuthoredRequestDefinitionBindsTo() {
        // then - the loader binds a protocol's block to this class, and the interpolator registry
        // checks it has an interpolator
        assertThat(underTest.definitionType()).isEqualTo(HttpRequestDefinition.class);
    }

    @Test
    void responseVars_isTheHttpVocabulary() {
        // given - inherited from the protocol interface, so every HTTP handler reports alike
        var response = new HttpResponse(201, Map.of("Location", List.of("/orders/7")), "{\"id\": 7}");

        // when
        var vars = underTest.responseVars(response);

        // then
        assertThat(vars).containsEntry("statusCode", 201).containsEntry("json", "{\"id\": 7}");
        assertThat(vars).isEqualTo(HttpResponseVars.of(response));
    }

    @Test
    void performRequest_rejectsAnyHandlerArgument() {
        // given - this handler understands none, and ignoring them would run the request without the
        // settings the author asked some other handler for
        var definition = new HttpRequestDefinition(
                stub.baseUrl() + "/orders", "GET", null, null, null, null, Map.of("certAlias", "client-a"));

        // when / then - before anything is sent, so the request is errored rather than half-made
        assertThatThrownBy(() -> underTest.performRequest(definition))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("certAlias")
                .hasMessageContaining("httpclient5");
        assertThat(stub.requestCount()).isZero();
    }

    // ---------- pool configuration ----------

    @Test
    void constructor_rejectsAPoolThatCannotWork() {
        // then - a per-host ceiling above the total states an intent the pool will not honour
        assertThatThrownBy(() -> new ApacheHttpRequestHandler(5, 10)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new ApacheHttpRequestHandler(0, 0)).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> new ApacheHttpRequestHandler(-1, -1)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_acceptsExplicitPoolLimits() {
        // given - the reason they are arguments: the library's 5-per-route default silently throttles
        try (var handler = new ApacheHttpRequestHandler(400, 100)) {
            stub.respond(200, "ok");

            // then
            assertThat(handler.performRequest(get("/orders")).statusCode()).isEqualTo(200);
        }
    }

    @Test
    void close_isSafeToCallTwice() {
        // given - who closes a handler is undecided; until then nothing should break if two owners do
        var handler = new ApacheHttpRequestHandler();
        handler.close();

        // then
        assertThatCode(handler::close).doesNotThrowAnyException();
    }

    private HttpRequestDefinition get(String path) {
        return new HttpRequestDefinition(stub.baseUrl() + path, "GET", null, null, null, null);
    }
}
