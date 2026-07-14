package dev.pbroman.brat.core.data;

import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

class HttpRequestDefinitionInterpolationTest {

    Interpolation interpolation = (input, runtimeData) -> new InterpolationOutcome(input + "-i", input + "-i");
    RuntimeData runtimeData = mock(RuntimeData.class);
    HttpRequestDefinitionInterpolation underTest = new HttpRequestDefinitionInterpolation(new AuthInterpolation());

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
    void interpolated_isCorrect() {
        // when
        var interpolated = underTest.interpolated(validRequest, interpolation, runtimeData);

        // then
        assertThat(interpolated.getUrl()).isEqualTo("http://url-i");
        assertThat(interpolated.getMethod()).isEqualTo("GET-i");
        assertThat(interpolated.getTimeout()).isEqualTo("30-i");
        assertThat(interpolated.getOutcomes().keySet()).containsExactly(
                "url", "method", "timeout", "body.raw", "body._bodyString", "header." + CONTENT_TYPE,
                "header.Authorization", "auth.type", "auth.token");
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

}
