package dev.pbroman.brat.core.reporting;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.reporting.ValueMask;
import dev.pbroman.brat.core.data.HttpBody;
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

import static dev.pbroman.brat.core.util.Constants.MASK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SensitiveValuesTest {

    private static final RequestCoordinates COORDINATES =
            new RequestCoordinates("suite/login", null, "login", Phase.MAIN, 1);

    private final ValueMask mask = new ValueMask();

    // --- fixtures ---

    /** A sent request carrying one header, whose outcome is secret-bearing or not. */
    private static HttpRequestDefinition sentWith(String name, String value, boolean fromSecret) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("header." + name, new InterpolationOutcome(value, name + " → " + value, fromSecret));
        return new HttpRequestDefinition("http://h/login", "POST", null, Map.of(name, value), null, outcomes);
    }

    private static HttpRequestDefinition sentWithNoHeaders() {
        return new HttpRequestDefinition("http://h/login", "POST", null, null, null, new LinkedHashMap<>());
    }

    private static Map<String, Object> response(Map<String, List<String>> headers, String body, boolean json) {
        var caseInsensitive = new TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(headers);
        var vars = new HashMap<String, Object>();
        vars.put("statusCode", 200);
        vars.put("headers", caseInsensitive);
        if (body != null) {
            vars.put("body", body);
            if (json) {
                vars.put("json", body);
            }
        }
        return vars;
    }

    private static Map<String, Object> jsonResponse(String body) {
        return response(Map.of("Content-Type", List.of("application/json")), body, true);
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

    private static RequestResult errored(RequestDefinition sent) {
        return new RequestResult(COORDINATES, sent, null, new RequestStatus.Errored("connection refused"), 10, 0, null);
    }

    /** Whether {@code value} is masked when guessed values are masked. */
    private boolean masked(String value) {
        return mask.mask(value, false).equals(MASK);
    }

    /** Whether {@code value} is masked even when guessed values are shown. */
    private boolean maskedAsSecret(String value) {
        return mask.mask(value, true).equals(MASK);
    }

    // --- arguments ---

    @Test
    void addTo_throwsForANullMask() {
        // given
        var result = result(sentWithNoHeaders(), jsonResponse("{}"));

        // when / then
        assertThatThrownBy(() -> SensitiveValues.addTo(null, result)).isInstanceOf(BratException.class);
    }

    @Test
    void addTo_throwsForANullResult() {
        // when / then
        assertThatThrownBy(() -> SensitiveValues.addTo(mask, null)).isInstanceOf(BratException.class);
    }

    // --- the request as sent ---

    @Test
    void addTo_addsTheCredentialOfAnAuthorizationHeaderAsGuessed() {
        // given
        var result = result(sentWith("Authorization", "Bearer abcd1234", false), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then - the scheme word stays readable, and showing secrets lifts the mask
        assertThat(mask.mask("Bearer abcd1234", false)).isEqualTo("Bearer ***");
        assertThat(mask.mask("Bearer abcd1234", true)).isEqualTo("Bearer abcd1234");
    }

    @Test
    void addTo_addsTheCredentialOfAHeaderBuiltFromASecretAsASecret() {
        // given
        var result = result(sentWith("Authorization", "Basic dXNlcjpwYXNz", true), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then
        assertThat(maskedAsSecret("dXNlcjpwYXNz")).isTrue();
    }

    @Test
    void addTo_addsTheCredentialOfAProxyAuthorizationHeader() {
        // given
        var result = result(sentWith("Proxy-Authorization", "Basic cHJveHk6cHc=", false), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then
        assertThat(masked("cHJveHk6cHc=")).isTrue();
    }

    @Test
    void addTo_matchesAHeaderNameIgnoringCase() {
        // given
        var result = result(sentWith("authorization", "Bearer abcd1234", false), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then
        assertThat(masked("abcd1234")).isTrue();
    }

    @Test
    void addTo_addsAnAuthorizationValueWithoutASpaceWhole() {
        // given
        var result = result(sentWith("Authorization", "abcd1234", false), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then
        assertThat(masked("abcd1234")).isTrue();
    }

    @Test
    void addTo_addsTheValueOfEveryPairOfACookieHeader() {
        // given
        var result = result(sentWith("Cookie", "SESSION=abcd1234; lang=english", false), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then - the names stay readable
        assertThat(mask.mask("SESSION=abcd1234; lang=english", false)).isEqualTo("SESSION=***; lang=***");
    }

    @Test
    void addTo_leavesAnyOtherRequestHeader() {
        // given
        var result = result(sentWith("X-Request-Id", "req-12345", false), jsonResponse("{}"));

        // when
        SensitiveValues.addTo(mask, result);

        // then
        assertThat(masked("req-12345")).isFalse();
    }

    @Test
    void addTo_takesNothingFromADefinitionThatWasNeverInterpolated() {
        // given - an authored definition: no outcomes, so nothing says how its headers were built
        var authored =
                new HttpRequestDefinition("http://h/login", "POST", null, Map.of("Authorization", "Bearer abcd1234"));
        var result = errored(authored);

        // when
        SensitiveValues.addTo(mask, result);

        // then
        assertThat(masked("abcd1234")).isFalse();
    }

    // --- a form body ---

    /** A sent request whose form holds one entry, its outcome secret-bearing or not. */
    private static HttpRequestDefinition sentWithForm(String key, String value, boolean fromSecret) {
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("body.form." + key, new InterpolationOutcome(value, key + " → " + value, fromSecret));
        return new HttpRequestDefinition(
                "http://h/token", "POST", HttpBody.form(Map.of(key, value)), null, null, outcomes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"client_secret", "password", "refresh_token", "clientSecret", "Client-Secret"})
    void addTo_addsASensitiveFormEntryTypedInClearAsGuessed(String key) {
        // when
        SensitiveValues.addTo(mask, result(sentWithForm(key, "value-1234", false), jsonResponse("{}")));

        // then
        assertThat(masked("value-1234")).isTrue();
        assertThat(mask.mask("value-1234", true)).isEqualTo("value-1234");
    }

    @Test
    void addTo_addsASensitiveFormEntryBuiltFromASecretAsASecret() {
        // when
        SensitiveValues.addTo(mask, result(sentWithForm("client_secret", "value-1234", true), jsonResponse("{}")));

        // then
        assertThat(maskedAsSecret("value-1234")).isTrue();
    }

    @Test
    void addTo_addsASensitiveFormEntryWithNoOutcomeAsGuessed() {
        // given - interpolated, but carrying no outcome for the entry
        var sent = new HttpRequestDefinition(
                "http://h/token",
                "POST",
                HttpBody.form(Map.of("password", "value-1234")),
                null,
                null,
                new LinkedHashMap<>());

        // when
        SensitiveValues.addTo(mask, result(sent, jsonResponse("{}")));

        // then
        assertThat(masked("value-1234")).isTrue();
        assertThat(mask.mask("value-1234", true)).isEqualTo("value-1234");
    }

    @Test
    void addTo_leavesAFormEntryWhoseNameIsNotSensitive() {
        // when
        SensitiveValues.addTo(mask, result(sentWithForm("client_id", "orders-tests", false), jsonResponse("{}")));

        // then
        assertThat(masked("orders-tests")).isFalse();
    }

    @Test
    void addTo_takesNothingFromTheFormOfADefinitionThatWasNeverInterpolated() {
        // given
        var authored = new HttpRequestDefinition(
                "http://h/token", "POST", HttpBody.form(Map.of("client_secret", "value-1234")), null);

        // when
        SensitiveValues.addTo(mask, errored(authored));

        // then
        assertThat(masked("value-1234")).isFalse();
    }

    // --- the response ---

    @Test
    void addTo_addsTheValueOfASetCookieHeaderAsGuessed() {
        // given
        var vars = response(Map.of("Set-Cookie", List.of("SESSION=abcd1234; Path=/; HttpOnly")), null, false);

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(mask.mask("SESSION=abcd1234; Path=/; HttpOnly", false)).isEqualTo("SESSION=***; Path=/; HttpOnly");
        assertThat(mask.mask("abcd1234", true)).isEqualTo("abcd1234");
    }

    @Test
    void addTo_addsEverySetCookieValue() {
        // given
        var vars = response(Map.of("Set-Cookie", List.of("A=first1234", "B=second1234")), null, false);

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(masked("first1234")).isTrue();
        assertThat(masked("second1234")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "refresh_token", "id_token", "client_secret", "password"})
    void addTo_addsTheValueOfEverySensitiveJsonField(String field) {
        // given
        var vars = jsonResponse("{\"" + field + "\": \"value-1234\", \"user\": \"alice-1234\"}");

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(masked("value-1234")).isTrue();
        assertThat(masked("alice-1234")).isFalse();
        assertThat(mask.mask("value-1234", true)).isEqualTo("value-1234");
    }

    @ParameterizedTest
    @ValueSource(strings = {"accessToken", "Access-Token", "ACCESS_TOKEN", "clientSecret"})
    void addTo_matchesAFieldNameIgnoringCaseUnderscoreAndHyphen(String field) {
        // given
        var vars = jsonResponse("{\"" + field + "\": \"value-1234\"}");

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(masked("value-1234")).isTrue();
    }

    @Test
    void addTo_findsAFieldAtAnyDepthAndInArrays() {
        // given
        var vars = jsonResponse("{\"data\": {\"session\": {\"access_token\": \"deep-1234\"}},"
                + " \"keys\": [{\"password\": \"inarray1\"}]}");

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(masked("deep-1234")).isTrue();
        assertThat(masked("inarray1")).isTrue();
    }

    @Test
    void addTo_leavesAFieldValueThatIsNotAString() {
        // given
        var vars = jsonResponse("{\"password\": 12345678}");

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(masked("12345678")).isFalse();
    }

    @Test
    void addTo_takesNothingFromABodyThatIsNotJson() {
        // given
        var vars = response(Map.of("Content-Type", List.of("text/plain")), "access_token=value-1234", false);

        // when
        SensitiveValues.addTo(mask, result(sentWithNoHeaders(), vars));

        // then
        assertThat(masked("value-1234")).isFalse();
    }

    @Test
    void addTo_takesTheRequestAloneFromARequestWithNoResponse() {
        // when
        SensitiveValues.addTo(mask, errored(sentWith("Authorization", "Bearer abcd1234", false)));

        // then
        assertThat(masked("abcd1234")).isTrue();
    }

    // --- other protocols ---

    @Test
    void addTo_takesNothingFromARequestOfAnotherProtocol() {
        // given
        RequestDefinition ftp = () -> "ftp";
        var vars = jsonResponse("{\"access_token\": \"value-1234\"}");

        // when
        SensitiveValues.addTo(mask, result(ftp, vars));

        // then
        assertThat(masked("value-1234")).isFalse();
    }

    // --- isSensitiveName ---

    @ParameterizedTest
    @ValueSource(strings = {"access_token", "accessToken", "Client-Secret", "PASSWORD", "id_token", "refresh_token"})
    void isSensitiveName_matchesIgnoringCaseUnderscoreAndHyphen(String name) {
        // when / then
        assertThat(SensitiveValues.isSensitiveName(name)).isTrue();
    }

    @Test
    void isSensitiveName_isFalseForAnyOtherName() {
        // when / then
        assertThat(SensitiveValues.isSensitiveName("client_id")).isFalse();
        assertThat(SensitiveValues.isSensitiveName("token_type")).isFalse();
    }

    @Test
    void isSensitiveName_throwsForNull() {
        // when / then
        assertThatThrownBy(() -> SensitiveValues.isSensitiveName(null)).isInstanceOf(BratException.class);
    }
}
