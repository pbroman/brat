package dev.pbroman.brat.core.interpolation.rules;

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
        return new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of(HEADERS, Map.of("Content-Type", "baa")));
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
    void noHeadersPresent_returnsInput() throws Exception {
        // given
        var input = "${response.headers.Content-Type}";
        runtimeData = new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of());

        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }
}
