package dev.pbroman.brat.core.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestAuthenticatorTest {

    private static final Auth AUTH = new Auth("sig", Map.of("secret", "s3cr3t"));

    private final HttpRequestDefinition request = new HttpRequestDefinition(
            "http://localhost/orders",
            "POST",
            Map.of("raw", "{\"id\":1}"),
            Map.of("Accept", "application/json"),
            Map.of("proxy", "on"),
            Map.of("url", new InterpolationOutcome("http://localhost/orders", "${vars.base}/orders → http://…")));

    @Test
    void authenticated_addsTheReturnedHeadersToACopy() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), request);

        // then
        assertThat(sent).isNotSameAs(request);
        assertThat(sent.getHeaders())
                .containsExactly(Map.entry("Accept", "application/json"), Map.entry("X-Sig", "abc"));
        assertThat(sent.getUrl()).isEqualTo(request.getUrl());
        assertThat(sent.getMethod()).isEqualTo(request.getMethod());
        assertThat(sent.getBody()).isEqualTo(request.getBody());
        assertThat(sent.getArgs()).isEqualTo(request.getArgs());
        assertThat(sent.isInterpolated()).isTrue();
    }

    @Test
    void authenticated_addsHeadersToARequestThatHasNone() {
        // given
        var bare = new HttpRequestDefinition("http://localhost/orders", "GET", null, null, null, Map.of());

        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), bare);

        // then
        assertThat(sent.getHeaders()).containsExactly(Map.entry("X-Sig", "abc"));
    }

    @Test
    void authenticated_doesNotModifyTheRequest() {
        // when
        RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), request);

        // then
        assertThat(request.getHeaders()).containsExactly(Map.entry("Accept", "application/json"));
        assertThat(request.getOutcomes()).containsOnlyKeys("url");
    }

    @Test
    void authenticated_callsTheHandlerOnceWithTheAuthAndTheRequest() {
        // given
        var handler = new RecordingHandler(Map.of("X-Sig", "abc"));

        // when
        RequestAuthenticator.authenticated(AUTH, handler, request);

        // then
        assertThat(handler.calls).hasSize(1);
        assertThat(handler.calls.get(0).auth()).isSameAs(AUTH);
        assertThat(handler.calls.get(0).request()).isSameAs(request);
    }

    @Test
    void authenticated_returnsTheRequestItselfWhenThereAreNoHeadersToAdd() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of()), request);

        // then
        assertThat(sent).isSameAs(request);
    }

    // ---------- outcomes ----------

    @Test
    void authenticated_keepsTheRequestsOutcomesAndAddsOnePerHeader() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), request);

        // then
        assertThat(sent.getOutcomes()).containsOnlyKeys("url", "header.X-Sig");
        assertThat(sent.getOutcomes().get("url"))
                .isEqualTo(request.getOutcomes().get("url"));
        var outcome = sent.getOutcomes().get("header.X-Sig");
        assertThat(outcome.value()).isEqualTo("abc");
        assertThat(outcome.reportingString()).isEqualTo("auth sig → ***");
        assertThat(outcome.containsSecret()).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "authorization", "Proxy-Authorization", "PROXY-AUTHORIZATION"})
    void authenticated_keepsTheSchemeWordOfAnAuthorizationHeader(String name) {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of(name, "Basic YWRtaW46c2VjcmV0")), request);

        // then
        var outcome = sent.getOutcomes().get("header." + name);
        assertThat(outcome.reportingString()).isEqualTo("auth sig → Basic ***");
        assertThat(outcome.value()).isEqualTo("Basic YWRtaW46c2VjcmV0");
    }

    @Test
    void authenticated_masksAnAuthorizationHeaderWithoutASchemeWhole() {
        // when - no space, so there is no scheme word to keep and the whole value is the credential
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("Authorization", "abc.def")), request);

        // then
        assertThat(sent.getOutcomes().get("header.Authorization").reportingString())
                .isEqualTo("auth sig → ***");
    }

    @Test
    void authenticated_masksEveryOtherHeaderWholeEvenWithASpace() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Api-Key", "Key abc")), request);

        // then
        assertThat(sent.getOutcomes().get("header.X-Api-Key").reportingString()).isEqualTo("auth sig → ***");
    }

    // ---------- refusals ----------

    @ParameterizedTest
    @ValueSource(strings = {"Accept", "accept", "ACCEPT"})
    void authenticated_rejectsAHeaderTheRequestAlreadyCarries(String name) {
        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(Map.of(name, "x-secret")), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(name)
                .hasMessageContaining("sig")
                .hasMessageContaining("type: none")
                .hasMessageNotContaining("x-secret");
    }

    @Test
    void authenticated_rejectsANullMapNamingTheType() {
        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(null), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig");
    }

    @Test
    void authenticated_rejectsANullHeaderName() {
        // given
        var headers = new HashMap<String, String>();
        headers.put(null, "s3cr3t");

        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(headers), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageNotContaining("s3cr3t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void authenticated_rejectsABlankHeaderName(String blank) {
        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(Map.of(blank, "s3cr3t")), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void authenticated_rejectsANullHeaderValueNamingTheHeader() {
        // given
        var headers = new HashMap<String, String>();
        headers.put("X-Sig", null);

        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(headers), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageContaining("X-Sig");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void authenticated_rejectsABlankHeaderValueNamingTheHeader(String blank) {
        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", blank)), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageContaining("X-Sig");
    }

    @Test
    void authenticated_rejectsTwoReturnedNamesDifferingOnlyInCase() {
        // given
        var headers = new LinkedHashMap<String, String>();
        headers.put("X-Sig", "s3cr3t-1");
        headers.put("x-sig", "s3cr3t-2");

        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(headers), request))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageNotContaining("s3cr3t-1")
                .hasMessageNotContaining("s3cr3t-2");
    }

    @Test
    void authenticated_letsWhatTheHandlerThrowsPropagateUnchanged() {
        // given
        var thrown = new BratException("The 'secret' argument is blank, which the 'sig' auth cannot use");

        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, throwing(thrown), request))
                .isSameAs(thrown);
    }

    // ---------- helpers ----------

    private static AuthHandler returning(Map<String, String> headers) {
        return new RecordingHandler(headers);
    }

    private static AuthHandler throwing(RuntimeException exception) {
        return new AuthHandler() {
            @Override
            public String type() {
                return "sig";
            }

            @Override
            public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
                throw exception;
            }
        };
    }

    /** One call to {@link AuthHandler#apply}. */
    private record Call(Auth auth, HttpRequestDefinition request) {}

    /** An auth handler of type {@code sig} returning fixed headers and recording each call. */
    private static final class RecordingHandler implements AuthHandler {

        private final Map<String, String> headers;
        private final List<Call> calls = new ArrayList<>();

        private RecordingHandler(Map<String, String> headers) {
            this.headers = headers;
        }

        @Override
        public String type() {
            return "sig";
        }

        @Override
        public Map<String, String> apply(Auth auth, HttpRequestDefinition request) {
            calls.add(new Call(auth, request));
            return headers;
        }
    }
}
