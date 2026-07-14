package dev.pbroman.brat.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;

class ConfigDataInterpolationUtilsTest {

    Interpolation interpolation = (input, runtimeData) -> new InterpolationOutcome(input + "-resolved", input + " -> " + input + "-resolved");
    RuntimeData runtimeData = mock(RuntimeData.class);

    @Test
    void checkNotInterpolated_doesNotThrowIfNotInterpolated() {
        // given
        var auth = new Auth();

        // when / then
        ConfigDataInterpolationUtils.checkNotInterpolated(auth);
    }

    @Test
    void checkNotInterpolated_throwsIfAlreadyInterpolated() {
        // given
        var auth = new Auth("none", null, null, null, Map.of());

        // when / then
        assertThatThrownBy(() -> ConfigDataInterpolationUtils.checkNotInterpolated(auth))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolateMapWithOutcomes_isCorrect() {
        // given
        var map = Map.of("key", "value");

        // when
        var result = ConfigDataInterpolationUtils.interpolateMapWithOutcomes(interpolation, runtimeData, map);

        // then
        assertThat(result.get("key").value()).isEqualTo("value-resolved");
    }

    @Test
    void interpolateMapWithOutcomes_returnsEmptyMapIfMapIsNull() {
        // when
        var result = ConfigDataInterpolationUtils.interpolateMapWithOutcomes(interpolation, runtimeData, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void interpolateIfPresent_interpolatesAndRecordsIfValuePresent() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var outcome = ConfigDataInterpolationUtils.interpolateIfPresent(interpolation, runtimeData, outcomes, "field", "value");

        // then
        assertThat(outcome.value()).isEqualTo("value-resolved");
        assertThat(outcomes).containsKey("field");
    }

    @Test
    void interpolateIfPresent_returnsNullAndSkipsRecordingIfValueIsNull() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var outcome = ConfigDataInterpolationUtils.interpolateIfPresent(interpolation, runtimeData, outcomes, "field", null);

        // then
        assertThat(outcome).isNull();
        assertThat(outcomes).isEmpty();
    }

    @Test
    void asStringOrNull_returnsStringValueIfOutcomePresent() {
        // given
        var outcome = new InterpolationOutcome("value", "value");

        // when / then
        assertThat(ConfigDataInterpolationUtils.asStringOrNull(outcome)).isEqualTo("value");
    }

    @Test
    void asStringOrNull_returnsNullIfOutcomeIsNull() {
        // when / then
        assertThat(ConfigDataInterpolationUtils.asStringOrNull(null)).isNull();
    }

}
