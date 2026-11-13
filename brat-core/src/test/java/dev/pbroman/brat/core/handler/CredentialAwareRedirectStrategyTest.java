package dev.pbroman.brat.core.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RequestOptions;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialAwareRedirectStrategyTest {

    private final CredentialAwareRedirectStrategy underTest = new CredentialAwareRedirectStrategy();

    private static HttpRequestDefinition withHeader(String name, String value) {
        return new HttpRequestDefinition("http://h/x", "GET", null, Map.of(name, value));
    }

    private static HttpClientContext context(boolean carriesCredentials) {
        var context = HttpClientContext.create();
        context.setAttribute(CredentialAwareRedirectStrategy.CARRIES_CREDENTIALS, carriesCredentials);
        return context;
    }

    private boolean allowed(String from, String to, boolean carriesCredentials) throws Exception {
        return underTest.isRedirectAllowed(
                HttpHost.create(from),
                HttpHost.create(to),
                new BasicHttpRequest("GET", "/"),
                context(carriesCredentials));
    }

    // --- carriesCredentials ---

    @ParameterizedTest
    @ValueSource(strings = {"Authorization", "authorization", "Cookie", "COOKIE"})
    void carriesCredentials_isTrueForAnAuthorizationOrCookieHeader(String name) {
        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(withHeader(name, "x"), new RequestOptions(null)))
                .isTrue();
    }

    @Test
    void carriesCredentials_isTrueForAnAuthThatIsNotNone() {
        // given
        var options = new RequestOptions(null, null, new Auth("apikey", Map.of("value", "k")), null);

        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(withHeader("X-Api-Key", "k"), options))
                .isTrue();
    }

    @Test
    void carriesCredentials_isFalseForAnAuthOfTypeNone() {
        // given
        var options = new RequestOptions(null, null, new Auth("none", null), null);

        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(withHeader("Accept", "*/*"), options))
                .isFalse();
    }

    @Test
    void carriesCredentials_isTrueForAHeaderBuiltFromASecret() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("header.X-Token", new InterpolationOutcome("t", "${secrets.t} → ***", true));
        var definition = new HttpRequestDefinition("http://h/x", "GET", null, Map.of("X-Token", "t"), null, outcomes);

        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(definition, new RequestOptions(null)))
                .isTrue();
    }

    @Test
    void carriesCredentials_isFalseForAPlainRequest() {
        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(
                        withHeader("Accept", "application/json"), new RequestOptions(null)))
                .isFalse();
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(
                        new HttpRequestDefinition("http://h/x", "GET", null, null), new RequestOptions(null)))
                .isFalse();
    }

    // --- isRedirectAllowed ---

    @Test
    void isRedirectAllowed_allowsTheSameOriginWithCredentials() throws Exception {
        // when / then
        assertThat(allowed("http://api.example:8080", "http://API.example:8080", true))
                .isTrue();
    }

    @Test
    void isRedirectAllowed_treatsAMissingPortAsTheSchemesDefault() throws Exception {
        // when / then
        assertThat(allowed("http://api.example", "http://api.example:80", true)).isTrue();
        assertThat(allowed("https://api.example:443", "https://api.example", true))
                .isTrue();
    }

    @Test
    void isRedirectAllowed_refusesAnotherHostWithCredentials() throws Exception {
        // when / then
        assertThat(allowed("http://api.example", "http://login.example", true)).isFalse();
    }

    @Test
    void isRedirectAllowed_refusesAnotherPortWithCredentials() throws Exception {
        // when / then
        assertThat(allowed("http://localhost:8080", "http://localhost:9090", true))
                .isFalse();
    }

    @Test
    void isRedirectAllowed_refusesAnotherSchemeWithCredentials() throws Exception {
        // when / then
        assertThat(allowed("http://api.example", "https://api.example", true)).isFalse();
    }

    @Test
    void isRedirectAllowed_comparesHostNamesNotAddresses() throws Exception {
        // when / then
        assertThat(allowed("http://localhost:8080", "http://127.0.0.1:8080", true))
                .isFalse();
    }

    @Test
    void isRedirectAllowed_allowsAnotherOriginWithoutCredentials() throws Exception {
        // when / then
        assertThat(allowed("http://api.example", "https://login.example:9443", false))
                .isTrue();
    }

    @Test
    void isRedirectAllowed_readsAMissingAttributeAsNoCredentials() throws Exception {
        // when / then
        assertThat(underTest.isRedirectAllowed(
                        HttpHost.create("http://a.example"),
                        HttpHost.create("http://b.example"),
                        new BasicHttpRequest("GET", "/"),
                        HttpClientContext.create()))
                .isTrue();
    }

    // --- what else counts as a credential ---

    private static HttpRequestDefinition withOutcome(String key, boolean secret, HttpBody body) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put(key, new InterpolationOutcome("v-12345", key + " → v-12345", secret));
        return new HttpRequestDefinition("http://h/x", "POST", body, null, null, outcomes);
    }

    @Test
    void carriesCredentials_isTrueForASecretInTheBody() {
        // given - a 307 or 308 would re-send it
        var definition = withOutcome("body.form.client_secret", true, HttpBody.form(Map.of("x", "v-12345")));

        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(definition, new RequestOptions(null)))
                .isTrue();
    }

    @Test
    void carriesCredentials_isTrueForASecretInTheUrl() {
        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(
                        withOutcome("url", true, null), new RequestOptions(null)))
                .isTrue();
    }

    @Test
    void carriesCredentials_isFalseForAHeaderOutcomeHoldingNoSecret() {
        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(
                        withOutcome("header.X-Trace", false, null), new RequestOptions(null)))
                .isFalse();
    }

    @Test
    void carriesCredentials_isTrueForASensitiveFormEntryTypedInClear() {
        // given
        var definition = new HttpRequestDefinition(
                "http://h/token", "POST", HttpBody.form(Map.of("client_secret", "abc-12345")), null);

        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(definition, new RequestOptions(null)))
                .isTrue();
    }

    @Test
    void carriesCredentials_isFalseForAFormWithNoSensitiveEntry() {
        // given
        var definition = new HttpRequestDefinition(
                "http://h/token", "POST", HttpBody.form(Map.of("grant_type", "client_credentials")), null);

        // when / then
        assertThat(CredentialAwareRedirectStrategy.carriesCredentials(definition, new RequestOptions(null)))
                .isFalse();
    }
}
