package dev.pbroman.brat.core.interpolation.configdata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
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
    HttpRequestDefinitionInterpolator underTest = new HttpRequestDefinitionInterpolator(new AuthInterpolator());

    HttpRequestDefinition validRequest;

    @BeforeEach
    void setUp() {
        var body = new LinkedHashMap<String, String>();
        body.put("raw", "{}");
        var headers = new LinkedHashMap<String, String>();
        headers.put(CONTENT_TYPE, "application/json");
        headers.put("Authorization", "Bearer token");
        var auth = new Auth("bearer", "token");
        validRequest = new HttpRequestDefinition("http://url", "GET", "30", body, headers, auth);
    }

    @Test
    void interpolated_handlesARequestWithoutAnAuthBlock() {
        // given - the constructor's Javadoc explicitly permits a null auth
        var request = new HttpRequestDefinition("http://url", "GET", null, null, null, null);

        // when
        var interpolated = underTest.interpolated(request, interpolation, runtimeData);

        // then - no auth.* outcomes, and a null auth on the copy
        assertThat(interpolated.getAuth()).isNull();
        assertThat(interpolated.getOutcomes()).doesNotContainKey("auth.type");
    }

    @Test
    void interpolated_isCorrect() {
        // when
        var interpolated = underTest.interpolated(validRequest, interpolation, runtimeData);

        // then
        assertThat(interpolated.getUrl()).isEqualTo("http://url-i");
        assertThat(interpolated.getMethod()).isEqualTo("GET-i");
        assertThat(interpolated.getTimeout()).isEqualTo("30-i");
        assertThat(interpolated.getOutcomes().keySet())
                .containsExactly(
                        "url",
                        "method",
                        "timeout",
                        "body.raw",
                        "body._bodyString",
                        "header." + CONTENT_TYPE,
                        "header.Authorization",
                        "auth.type",
                        "auth.token");
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
    void interpolated_skipsNullTimeoutAndHandlesNullBodyAndHeaders() {
        // given
        var request = new HttpRequestDefinition("http://url", "GET", null, null, null, new Auth());

        // when
        var interpolated = underTest.interpolated(request, interpolation, runtimeData);

        // then
        assertThat(interpolated.getTimeout()).isNull();
        assertThat(interpolated.getOutcomes()).doesNotContainKey("timeout");
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
        var request = new HttpRequestDefinition("http://url", "POST", null, Map.of("file", "file:" + file), null, null);
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
        var request = new HttpRequestDefinition("http://url", "POST", null, Map.of("file", "file:" + file), null, null);
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
        var request = new HttpRequestDefinition("http://url", "POST", null, Map.of("file", "order.json"), null, null);
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
                "http://url", "POST", null, Map.of("file", "file:" + bodyDir.resolve("absent.json")), null, null);
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
                "http://url", "POST", null, Map.of("file", "file:" + bodyDir.resolve("payload.json")), null, null);
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
}
