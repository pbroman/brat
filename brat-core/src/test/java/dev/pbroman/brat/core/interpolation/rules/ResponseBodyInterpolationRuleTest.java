package dev.pbroman.brat.core.interpolation.rules;

import static dev.pbroman.brat.core.util.Constants.BODY;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;

class ResponseBodyInterpolationRuleTest extends AbstractInterpolationTest {

    private final String input = "${rb}";

    @BeforeEach
    void setUp() {
        underTest = new ResponseBodyInterpolationRule(tools);
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of(BODY, "baa"));
    }

    @Test
    void happyPath() throws Exception {
        // given
        var expected = "baa";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void bodyIsNotPresent_returnsNull() throws Exception {
        // given
        runtimeData = new RuntimeData(Map.of(), Map.of(), Map.of(), Map.of());

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isNull();
    }

}