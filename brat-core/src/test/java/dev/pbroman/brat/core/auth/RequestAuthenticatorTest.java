package dev.pbroman.brat.core.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestAuthenticatorTest {

    /** Credentials built from a secret: interpolated, with a secret-bearing outcome. */
    private static final Auth AUTH = new Auth(
            "sig",
            Map.of("secret", "s3cr3t"),
            Map.of("args.secret", new InterpolationOutcome("s3cr3t", "${secrets.sig} → ***", true)));

    /** Credentials typed into the suite or captured from a response: interpolated, nothing secret. */
    private static final Auth CLEAR_AUTH = new Auth(
            "sig",
            Map.of("token", "abcd1234"),
            Map.of("args.token", new InterpolationOutcome("abcd1234", "${vars.token} → abcd1234")));

    private final ValueMask mask = new ValueMask();

    private final HttpRequestDefinition request = new HttpRequestDefinition(
            "http://localhost/orders",
            "POST",
            HttpBody.raw("{\"id\":1}"),
            Map.of("Accept", "application/json"),
            Map.of("proxy", "on"),
            Map.of("url", new InterpolationOutcome("http://localhost/orders", "${vars.base}/orders → http://…")));

    @Test
    void authenticated_addsTheReturnedHeadersToACopy() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), request, mask);

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
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), bare, mask);

        // then
        assertThat(sent.getHeaders()).containsExactly(Map.entry("X-Sig", "abc"));
    }

    @Test
    void authenticated_doesNotModifyTheRequest() {
        // when
        RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), request, mask);

        // then
        assertThat(request.getHeaders()).containsExactly(Map.entry("Accept", "application/json"));
        assertThat(request.getOutcomes()).containsOnlyKeys("url");
    }

    @Test
    void authenticated_callsTheHandlerOnceWithTheAuthAndTheRequest() {
        // given
        var handler = new RecordingHandler(Map.of("X-Sig", "abc"));

        // when
        RequestAuthenticator.authenticated(AUTH, handler, request, mask);

        // then
        assertThat(handler.calls).hasSize(1);
        assertThat(handler.calls.get(0).auth()).isSameAs(AUTH);
        assertThat(handler.calls.get(0).request()).isSameAs(request);
    }

    @Test
    void authenticated_returnsTheRequestItselfWhenThereAreNoHeadersToAdd() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of()), request, mask);

        // then
        assertThat(sent).isSameAs(request);
    }

    // ---------- outcomes ----------

    @Test
    void authenticated_keepsTheRequestsOutcomesAndAddsOnePerHeader() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", "abc")), request, mask);

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
        var sent = RequestAuthenticator.authenticated(
                AUTH, returning(Map.of(name, "Basic YWRtaW46c2VjcmV0")), request, mask);

        // then
        var outcome = sent.getOutcomes().get("header." + name);
        assertThat(outcome.reportingString()).isEqualTo("auth sig → Basic ***");
        assertThat(outcome.value()).isEqualTo("Basic YWRtaW46c2VjcmV0");
    }

    @Test
    void authenticated_masksAnAuthorizationHeaderWithoutASchemeWhole() {
        // when - no space, so there is no scheme word to keep and the whole value is the credential
        var sent =
                RequestAuthenticator.authenticated(AUTH, returning(Map.of("Authorization", "abc.def")), request, mask);

        // then
        assertThat(sent.getOutcomes().get("header.Authorization").reportingString())
                .isEqualTo("auth sig → ***");
    }

    @Test
    void authenticated_masksEveryOtherHeaderWholeEvenWithASpace() {
        // when
        var sent = RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Api-Key", "Key abc")), request, mask);

        // then
        assertThat(sent.getOutcomes().get("header.X-Api-Key").reportingString()).isEqualTo("auth sig → ***");
    }

    // ---------- refusals ----------

    @ParameterizedTest
    @ValueSource(strings = {"Accept", "accept", "ACCEPT"})
    void authenticated_rejectsAHeaderTheRequestAlreadyCarries(String name) {
        // when / then
        assertThatThrownBy(() ->
                        RequestAuthenticator.authenticated(AUTH, returning(Map.of(name, "x-secret")), request, mask))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(name)
                .hasMessageContaining("sig")
                .hasMessageContaining("type: none")
                .hasMessageNotContaining("x-secret");
    }

    @Test
    void authenticated_rejectsANullMapNamingTheType() {
        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(null), request, mask))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig");
    }

    @Test
    void authenticated_rejectsANullHeaderName() {
        // given
        var headers = new HashMap<String, String>();
        headers.put(null, "s3cr3t");

        // when / then
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(headers), request, mask))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageNotContaining("s3cr3t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void authenticated_rejectsABlankHeaderName(String blank) {
        // when / then
        assertThatThrownBy(() ->
                        RequestAuthenticator.authenticated(AUTH, returning(Map.of(blank, "s3cr3t")), request, mask))
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
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(headers), request, mask))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sig")
                .hasMessageContaining("X-Sig");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void authenticated_rejectsABlankHeaderValueNamingTheHeader(String blank) {
        // when / then
        assertThatThrownBy(() ->
                        RequestAuthenticator.authenticated(AUTH, returning(Map.of("X-Sig", blank)), request, mask))
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
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, returning(headers), request, mask))
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
        assertThatThrownBy(() -> RequestAuthenticator.authenticated(AUTH, throwing(thrown), request, mask))
                .isSameAs(thrown);
    }

    // ---------- helpers ----------

    @Test
    void authenticated_addsTheCredentialOfASecretBuiltHeaderAsASecret() {
        // when
        RequestAuthenticator.authenticated(
                AUTH, returning(Map.of("Authorization", "Basic YWRtaW46c2VjcmV0")), request, mask);

        // then - masked even when a report shows guessed values
        assertThat(mask.mask("Basic YWRtaW46c2VjcmV0", true)).isEqualTo("Basic ***");
    }

    @Test
    void authenticated_reportsClearCredentialsInClear() {
        // when
        var sent = RequestAuthenticator.authenticated(
                CLEAR_AUTH, returning(Map.of("Authorization", "Bearer abcd1234")), request, mask);

        // then
        var outcome = sent.getOutcomes().get("header.Authorization");
        assertThat(outcome.value()).isEqualTo("Bearer abcd1234");
        assertThat(outcome.reportingString()).isEqualTo("auth sig → Bearer abcd1234");
        assertThat(outcome.containsSecret()).isFalse();
    }

    @Test
    void authenticated_addsTheCredentialOfAClearHeaderAsGuessed() {
        // when
        RequestAuthenticator.authenticated(
                CLEAR_AUTH, returning(Map.of("Authorization", "Bearer abcd1234")), request, mask);

        // then - masked by default, the scheme word kept, and shown when asked
        assertThat(mask.mask("auth sig → Bearer abcd1234", false)).isEqualTo("auth sig → Bearer ***");
        assertThat(mask.mask("auth sig → Bearer abcd1234", true)).isEqualTo("auth sig → Bearer abcd1234");
    }

    @Test
    void authenticated_addsTheWholeValueOfAnyOtherHeader() {
        // when
        RequestAuthenticator.authenticated(CLEAR_AUTH, returning(Map.of("X-Api-Key", "Key abcd1234")), request, mask);

        // then
        assertThat(mask.mask("Key abcd1234", false)).isEqualTo("***");
    }

    @Test
    void authenticated_treatsAnAuthThatWasNeverInterpolatedAsClear() {
        // given - no outcomes, so nothing in it was built from a secret
        var authored = new Auth("sig", Map.of("token", "abcd1234"));

        // when
        var sent = RequestAuthenticator.authenticated(
                authored, returning(Map.of("Authorization", "Bearer abcd1234")), request, mask);

        // then
        assertThat(sent.getOutcomes().get("header.Authorization").containsSecret())
                .isFalse();
        assertThat(mask.mask("abcd1234", false)).isEqualTo("***");
    }

    @Test
    void authenticated_addsNothingToTheMaskWhenThereAreNoHeadersToAdd() {
        // when
        RequestAuthenticator.authenticated(CLEAR_AUTH, returning(Map.of()), request, mask);

        // then
        assertThat(mask.mask("abcd1234", false)).isEqualTo("abcd1234");
    }

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
