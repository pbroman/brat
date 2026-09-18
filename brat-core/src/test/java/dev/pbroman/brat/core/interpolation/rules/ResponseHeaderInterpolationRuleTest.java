package dev.pbroman.brat.core.interpolation.rules;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ResponseHeaderInterpolationRuleTest extends AbstractInterpolationTest {

    @Override
    protected String ownToken() {
        return "${response.headers.moo}";
    }

    @BeforeEach
    void setUp() {
        underTest = new ResponseHeaderInterpolationRule();
    }

    protected RuntimeData setUpRuntimeData() {
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        runtimeData.setResponseVars(Map.of(HEADERS, Map.of("Content-Type", List.of("baa"))));
        return runtimeData;
    }

    @Test
    void happyPath() throws Exception {
        // given
        var input = "${response.headers.Content-Type}";
        var expected = "baa";

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void headerNotPresent_returnsInput() throws Exception {
        // given
        var input = "${response.headers.Bollocks}";

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void repeatedHeader_substitutesTheFirstValue() throws Exception {
        // given - the namespace keeps every value; a token can only stand for one
        var runtimeDataWithCookies = new RuntimeData(Map.of(), Map.of());
        runtimeDataWithCookies.setResponseVars(
                Map.of(HEADERS, Map.of("Set-Cookie", List.of("session=abc; Path=/", "theme=dark"))));

        // when
        var result = interpolate("${response.headers.Set-Cookie}", runtimeDataWithCookies);

        // then - never the list's own text, which would put [a, b] on the wire
        assertThat(result).isEqualTo("session=abc; Path=/");
    }

    @Test
    void headerWithNoValues_returnsInput() throws Exception {
        // given - a name carrying nothing is nothing to substitute, so it takes the missing path
        var runtimeDataWithEmpty = new RuntimeData(Map.of(), Map.of());
        runtimeDataWithEmpty.setResponseVars(Map.of(HEADERS, Map.of("X-Empty", List.of())));
        var input = "${response.headers.X-Empty}";

        // when
        var result = interpolate(input, runtimeDataWithEmpty);

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void headerLookupIgnoresTheCaseTheServerSent() throws Exception {
        // given - HTTP/2 servers commonly lowercase every name
        var runtimeDataLowercased = new RuntimeData(Map.of(), Map.of());
        runtimeDataLowercased.setResponseVars(Map.of(HEADERS, Map.of("content-type", List.of("application/json"))));

        // when
        var result = interpolate("${response.headers.Content-Type}", runtimeDataLowercased);

        // then
        assertThat(result).isEqualTo("application/json");
    }

    @Test
    void noHeadersPresent_returnsInput() throws Exception {
        // given
        var input = "${response.headers.Content-Type}";
        runtimeData = new RuntimeData(Map.of(), Map.of());

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }
}
