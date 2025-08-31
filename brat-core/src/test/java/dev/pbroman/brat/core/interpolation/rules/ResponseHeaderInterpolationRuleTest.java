package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;

class ResponseHeaderInterpolationRuleTest extends AbstractInterpolationTest {


    @BeforeEach
    void setUp() {
        underTest = new ResponseHeaderInterpolationRule(tools);
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of(HEADERS, Map.of("Content-Type", "baa")));
    }

    @Test
    void happyPath() throws Exception {
        // given
        var input = "${rh.Content-Type}";
        var expected = "baa";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void headerNotPresent_returnsInput() throws Exception {
        // given
        var input = "${rh.Bollocks}";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void noHeadersPresent_returnsInput() throws Exception {
        // given
        var input = "${rh.Content-Type}";
        runtimeData = new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of());

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(input);
    }

}