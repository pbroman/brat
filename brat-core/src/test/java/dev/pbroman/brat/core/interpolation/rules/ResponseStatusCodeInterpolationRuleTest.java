package dev.pbroman.brat.core.interpolation.rules;

import java.util.Map;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.AbstractInterpolationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseStatusCodeInterpolationRuleTest extends AbstractInterpolationTest {

    @Override
    protected String ownToken() {
        return "${response.statusCode}";
    }

    private final String input = "${response.statusCode}";

    @BeforeEach
    void setUp() {
        underTest = new ResponseStatusCodeInterpolationRule();
    }

    protected RuntimeData setUpRuntimeData() {
        var runtimeData = new RuntimeData(Map.of(), Map.of());
        runtimeData.setResponseVars(Map.of(STATUS_CODE, 200));
        return runtimeData;
    }

    @Test
    void happyPath() {
        // when
        var result = interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo("200");
    }

    @Test
    void statusCodeIsNotPresent_throwsException() {
        // given
        runtimeData = new RuntimeData(Map.of(), Map.of());

        // then
        assertThatThrownBy(() -> interpolate(input, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void outcome_declinesATokenThatOnlyStartsWithTheNamespace() {
        // when - the namespace carries no key, so the token must be exactly it
        var result = underTest.outcome("${response.statusCode.extra}", runtimeData);

        // then
        assertThat(result).isEmpty();
    }
}
