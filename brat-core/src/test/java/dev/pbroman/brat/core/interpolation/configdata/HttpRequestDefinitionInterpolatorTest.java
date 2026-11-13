package dev.pbroman.brat.core.interpolation.configdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HttpRequestDefinitionInterpolatorTest {

    Interpolation interpolation = (input, runtimeData) -> new InterpolationOutcome(input + "-i", input + "-i");
    RuntimeData runtimeData = mock(RuntimeData.class);
    HttpRequestDefinitionInterpolator underTest = new HttpRequestDefinitionInterpolator();

    HttpRequestDefinition validRequest;

    @BeforeEach
    void setUp() {
        var body = HttpBody.raw("{}");
        var headers = new LinkedHashMap<String, String>();
        headers.put(CONTENT_TYPE, "application/json");
        headers.put("Authorization", "Bearer token");
        validRequest = new HttpRequestDefinition("http://url", "GET", body, headers);
    }

    @Test
    void interpolated_isCorrect() {
        // when
        var interpolated = underTest.interpolated(validRequest, interpolation, runtimeData);

        // then
        assertThat(interpolated.getUrl()).isEqualTo("http://url-i");
        assertThat(interpolated.getMethod()).isEqualTo("GET-i");
        assertThat(interpolated.getOutcomes().keySet())
                .containsExactly("url", "method", "body.raw", "header." + CONTENT_TYPE, "header.Authorization");
        assertThat(interpolated.getHeaders()).containsKey(CONTENT_TYPE);
    }

    @Test
    void interpolated_throwsExceptionIfCopy() {
        // given
        var interpolated = underTest.interpolated(validRequest, interpolation, runtimeData);

        // then
        assertThatThrownBy(() -> underTest.interpolated(interpolated, interpolation, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolated_handlesNullBodyAndHeaders() {
        // given
        var request = new HttpRequestDefinition("http://url", "GET", null, null);

        // when
        var interpolated = underTest.interpolated(request, interpolation, runtimeData);

        // then
        assertThat(interpolated.getBody()).isNull();
        assertThat(interpolated.getHeaders()).isNull();
    }

    // --- file bodies ---

    @TempDir
    Path bodyDir;

    /**
     * Leaves its input alone, unlike the class's {@code interpolation}, which appends to everything —
     * including a path, which would then name no file on disk.
     */
    Interpolation passthrough = (input, data) -> new InterpolationOutcome(input, input);

    @Test
    void interpolated_resolvesAFileBodyIntoBodyString() throws IOException {
        // given - a handler must receive a payload it never has to read from disk
        var file = bodyDir.resolve("order.json");
        Files.writeString(file, "{\"id\": 1}");
        var request = new HttpRequestDefinition("http://url", "POST", HttpBody.file("file:" + file), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then
        assertThat(interpolated.getBody().getPayload()).contains("{\"id\": 1}");
    }

    @Test
    void interpolated_reportsAFileBodyByPathNotByContent() throws IOException {
        // given
        var file = bodyDir.resolve("secret.json");
        Files.writeString(file, "{\"password\": \"hunter2\"}");
        var request = new HttpRequestDefinition("http://url", "POST", HttpBody.file("file:" + file), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then - a reporting string travels into logs, and a body file may hold a credential
        assertThat(interpolated.getOutcomes().get("body.fileContent").reportingString())
                .contains("secret.json")
                .doesNotContain("hunter2");
    }

    @Test
    void interpolated_resolvesABareFileBodyPathAgainstTheSuite() throws IOException {
        // given
        var file = bodyDir.resolve("order.json");
        Files.writeString(file, "{}");
        var request = new HttpRequestDefinition("http://url", "POST", HttpBody.file("order.json"), null);
        var data = new RuntimeData(
                Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), "file:" + bodyDir.resolve("orders.yaml"));

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then
        assertThat(interpolated.getBody().getFileContent()).isNotNull();
    }

    @Test
    void interpolated_throwsWhenAFileBodyCannotBeRead() {
        // given
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.file("file:" + bodyDir.resolve("absent.json")), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // then
        assertThatThrownBy(() -> underTest.interpolated(request, passthrough, data))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("absent.json");
    }

    @Test
    void interpolated_interpolatesTheFileBodyPathBeforeReadingIt() throws IOException {
        // given - the file on disk is named as the path becomes *after* interpolation, so only a read
        // that happens after it can find the file at all
        Files.writeString(bodyDir.resolve("payload.json-i"), "{}");
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.file("file:" + bodyDir.resolve("payload.json")), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, interpolation, data);

        // then
        assertThat(interpolated.getBody().getFileContent()).isNotNull();
    }

    @Test
    void definitionType_isTheClassThisIsLookedUpBy() {
        // then - the registry keys on it, and lookup is by exact class so a subclass is not a match
        assertThat(underTest.definitionType()).isEqualTo(HttpRequestDefinition.class);
    }

    @Test
    void interpolated_resolvesTheHandlerArgsAndKeysTheirOutcomes() {
        // given - args interpolate like headers, so ${secrets.…} inside one resolves and is masked
        var args = new LinkedHashMap<String, String>();
        args.put("certAlias", "${vars.alias}");
        var definition = new HttpRequestDefinition("http://url", "GET", null, null, args);

        // when
        var interpolated = underTest.interpolated(definition, interpolation, runtimeData);

        // then
        assertThat(interpolated.getArgs()).containsEntry("certAlias", "${vars.alias}-i");
        assertThat(interpolated.getOutcomes()).containsKey("args.certAlias");
    }

    @Test
    void interpolated_leavesArgsEmptyWhenTheRequestDeclaresNone() {
        // given
        var definition = new HttpRequestDefinition("http://url", "GET", null, null);

        // when
        var interpolated = underTest.interpolated(definition, interpolation, runtimeData);

        // then - empty rather than null, and no stray args.* outcome keys
        assertThat(interpolated.getArgs()).isEmpty();
        assertThat(interpolated.getOutcomes().keySet()).noneMatch(key -> key.startsWith("args."));
    }

    // --- the body ---

    /** Resolves {@code ${secrets.x}} to a value holding {@code &}, marked secret; anything else as it is. */
    Interpolation secrets = (input, data) -> "${secrets.x}".equals(input)
            ? new InterpolationOutcome("a&b=c", input + " → ***", true)
            : new InterpolationOutcome(input, input);

    private static Map<String, String> form(String... keysAndValues) {
        var form = new LinkedHashMap<String, String>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            form.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return form;
    }

    @Test
    void interpolated_resolvesARawBodyUnderOneOutcome() {
        // when
        var interpolated = underTest.interpolated(validRequest, interpolation, runtimeData);

        // then
        assertThat(interpolated.getBody().getRaw()).isEqualTo("{}-i");
        assertThat(interpolated.getOutcomes()).containsKey("body.raw");
    }

    @Test
    void interpolated_reportsAFileBodyByItsPathAndItsContent() throws IOException {
        // given
        var file = bodyDir.resolve("order.json");
        Files.writeString(file, "{}");
        var request = new HttpRequestDefinition("http://url", "POST", HttpBody.file("file:" + file), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then
        assertThat(interpolated.getOutcomes()).containsKeys("body.file", "body.fileContent");
        assertThat(interpolated.getBody().getFile()).isEqualTo("file:" + file);
    }

    @Test
    void interpolated_resolvesEachFormEntryUnderItsOwnOutcomeInOrder() {
        // given
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.form(form("grant_type", "password", "username", "${vars.user}")), null);

        // when
        var interpolated = underTest.interpolated(request, interpolation, runtimeData);

        // then
        assertThat(interpolated.getBody().getForm())
                .containsExactly(Map.entry("grant_type", "password-i"), Map.entry("username", "${vars.user}-i"));
        assertThat(interpolated.getOutcomes().keySet())
                .containsSubsequence("body.form.grant_type", "body.form.username");
    }

    @Test
    void interpolated_keepsASecretFormEntryMaskedInItsOutcome() {
        // given
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.form(form("client_secret", "${secrets.x}")), null);

        // when
        var interpolated = underTest.interpolated(request, secrets, runtimeData);

        // then
        var outcome = interpolated.getOutcomes().get("body.form.client_secret");
        assertThat(outcome.containsSecret()).isTrue();
        assertThat(outcome.reportingString()).isEqualTo("${secrets.x} → ***");
    }

    @Test
    void interpolated_encodesTheResolvedValuesSoAnAmpersandArrivesIntact() {
        // given - the secret resolves to text holding & and =
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.form(form("client_secret", "${secrets.x}", "a", "1")), null);

        // when
        var interpolated = underTest.interpolated(request, secrets, runtimeData);

        // then
        assertThat(interpolated.getBody().getPayload()).isEqualTo("client_secret=a%26b%3Dc&a=1");
    }

    @Test
    void interpolated_throwsForAFormValueThatResolvesToJsonNull() {
        // given
        Interpolation toNull = (input, data) -> new InterpolationOutcome(null, input + " → null");
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.form(form("id", "${response.json.$.id}")), null);

        // when / then
        assertThatThrownBy(() -> underTest.interpolated(request, toNull, runtimeData))
                .isInstanceOf(BratException.class);
    }

    // --- a form's Content-Type ---

    @Test
    void interpolated_addsTheFormContentTypeAfterTheAuthoredHeaders() {
        // given
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.form(form("a", "1")), Map.of("Accept", "application/json"));

        // when
        var interpolated = underTest.interpolated(request, passthrough, runtimeData);

        // then
        assertThat(interpolated.getHeaders())
                .containsExactly(
                        Map.entry("Accept", "application/json"),
                        Map.entry("Content-Type", "application/x-www-form-urlencoded"));
        var outcome = interpolated.getOutcomes().get("header.Content-Type");
        assertThat(outcome.value()).isEqualTo("application/x-www-form-urlencoded");
        assertThat(outcome.reportingString()).isEqualTo("body form → application/x-www-form-urlencoded");
        assertThat(outcome.containsSecret()).isFalse();
    }

    @Test
    void interpolated_addsTheFormContentTypeWhenThereAreNoHeaders() {
        // given
        var request = new HttpRequestDefinition("http://url", "POST", HttpBody.form(form("a", "1")), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, runtimeData);

        // then
        assertThat(interpolated.getHeaders())
                .containsExactly(Map.entry("Content-Type", "application/x-www-form-urlencoded"));
    }

    @Test
    void interpolated_keepsADeclaredFormContentTypeWhateverItsCase() {
        // given
        var request = new HttpRequestDefinition(
                "http://url",
                "POST",
                HttpBody.form(form("a", "1")),
                Map.of("content-type", "application/x-www-form-urlencoded; charset=UTF-8"));

        // when
        var interpolated = underTest.interpolated(request, passthrough, runtimeData);

        // then - kept as written, and not added a second time
        assertThat(interpolated.getHeaders())
                .containsExactly(Map.entry("content-type", "application/x-www-form-urlencoded; charset=UTF-8"));
    }

    @Test
    void interpolated_throwsWhenADeclaredContentTypeResolvesToAnotherType() {
        // given
        Interpolation json = (input, data) -> "${env.contentType}".equals(input)
                ? new InterpolationOutcome("application/json", input + " → application/json")
                : new InterpolationOutcome(input, input);
        var request = new HttpRequestDefinition(
                "http://url", "POST", HttpBody.form(form("a", "1")), Map.of("Content-Type", "${env.contentType}"));

        // when / then
        assertThatThrownBy(() -> underTest.interpolated(request, json, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("application/json");
    }

    @Test
    void interpolated_addsNoContentTypeForABodyThatIsNotAForm() {
        // given
        var request = new HttpRequestDefinition("http://url", "POST", HttpBody.raw("{}"), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, runtimeData);

        // then
        assertThat(interpolated.getHeaders()).isNull();
    }
}
