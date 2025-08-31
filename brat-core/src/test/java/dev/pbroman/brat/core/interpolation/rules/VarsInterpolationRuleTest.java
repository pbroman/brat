package dev.pbroman.brat.core.interpolation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;

class VarsInterpolationRuleTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        underTest = new VarsInterpolationRule(tools);
    }

    protected RuntimeData setUpRuntimeData() {
        return new RuntimeData(Map.of(), Map.of(), Map.of("moo", "baa"), Map.of());
    }

    @Test
    void happyPath() throws Exception {
        // given
        var input = "${vars.moo}";
        var expected = "baa";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void onMissingReplacement_returnsEmptyString() throws Exception {
        // given
        var input = "${vars.missing}";
        var expected = "";

        // when
        var result = underTest.interpolate(input, runtimeData);

        //then
        assertThat(result).isEqualTo(expected);
    }

}