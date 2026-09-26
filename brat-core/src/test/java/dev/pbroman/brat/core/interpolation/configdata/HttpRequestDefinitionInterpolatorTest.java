package dev.pbroman.brat.core.interpolation.configdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
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
        var body = new LinkedHashMap<String, String>();
        body.put("raw", "{}");
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
                .containsExactly(
                        "url",
                        "method",
                        "body.raw",
                        "body._bodyString",
                        "header." + CONTENT_TYPE,
                        "header.Authorization");
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
        var request = new HttpRequestDefinition("http://url", "POST", Map.of("file", "file:" + file), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then
        assertThat(interpolated.getBody()).containsKey("_bodyString");
        assertThat(interpolated.getBody().get("_bodyString")).contains("{\"id\": 1}");
    }

    @Test
    void interpolated_reportsAFileBodyByPathNotByContent() throws IOException {
        // given
        var file = bodyDir.resolve("secret.json");
        Files.writeString(file, "{\"password\": \"hunter2\"}");
        var request = new HttpRequestDefinition("http://url", "POST", Map.of("file", "file:" + file), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then - a reporting string travels into logs, and a body file may hold a credential
        assertThat(interpolated.getOutcomes().get("body._bodyString").reportingString())
                .contains("secret.json")
                .doesNotContain("hunter2");
    }

    @Test
    void interpolated_resolvesABareFileBodyPathAgainstTheSuite() throws IOException {
        // given
        var file = bodyDir.resolve("order.json");
        Files.writeString(file, "{}");
        var request = new HttpRequestDefinition("http://url", "POST", Map.of("file", "order.json"), null);
        var data = new RuntimeData(
                Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), "file:" + bodyDir.resolve("orders.yaml"));

        // when
        var interpolated = underTest.interpolated(request, passthrough, data);

        // then
        assertThat(interpolated.getBody()).containsKey("_bodyString");
    }

    @Test
    void interpolated_throwsWhenAFileBodyCannotBeRead() {
        // given
        var request = new HttpRequestDefinition(
                "http://url", "POST", Map.of("file", "file:" + bodyDir.resolve("absent.json")), null);
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
                "http://url", "POST", Map.of("file", "file:" + bodyDir.resolve("payload.json")), null);
        var data = new RuntimeData(Map.of(), Map.of(), new LinkedHashMap<>(), Map.of(), null);

        // when
        var interpolated = underTest.interpolated(request, interpolation, data);

        // then
        assertThat(interpolated.getBody()).containsKey("_bodyString");
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
}
