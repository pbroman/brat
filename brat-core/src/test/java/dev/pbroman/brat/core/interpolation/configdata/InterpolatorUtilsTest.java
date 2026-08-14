package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InterpolatorUtilsTest {

    Interpolation interpolation =
            (input, runtimeData) -> new InterpolationOutcome(input + "-resolved", input + " -> " + input + "-resolved");
    RuntimeData runtimeData = mock(RuntimeData.class);

    @Test
    void checkNotInterpolated_doesNotThrowIfNotInterpolated() {
        // given
        var auth = new Auth();

        // when / then
        InterpolatorUtils.checkNotInterpolated(auth);
    }

    @Test
    void checkNotInterpolated_throwsIfNull() {
        // when / then - a backstop, so that a null never leaves an interpolator as an NPE
        assertThatThrownBy(() -> InterpolatorUtils.checkNotInterpolated(null)).isInstanceOf(BratException.class);
    }

    @Test
    void checkNotInterpolated_throwsIfAlreadyInterpolated() {
        // given
        var auth = new Auth("none", null, null, null, Map.of());

        // when / then
        assertThatThrownBy(() -> InterpolatorUtils.checkNotInterpolated(auth)).isInstanceOf(BratException.class);
    }

    @Test
    void interpolateMapWithOutcomes_isCorrect() {
        // given
        var map = Map.of("key", "value");

        // when
        var result = InterpolatorUtils.interpolateMapWithOutcomes(interpolation, runtimeData, map);

        // then
        assertThat(result.get("key").value()).isEqualTo("value-resolved");
    }

    @Test
    void interpolateMapWithOutcomes_returnsEmptyMapIfMapIsNull() {
        // when
        var result = InterpolatorUtils.interpolateMapWithOutcomes(interpolation, runtimeData, null);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void interpolateIfPresent_interpolatesAndRecordsIfValuePresent() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var outcome = InterpolatorUtils.interpolateIfPresent(interpolation, runtimeData, outcomes, "field", "value");

        // then
        assertThat(outcome.value()).isEqualTo("value-resolved");
        assertThat(outcomes).containsKey("field");
    }

    @Test
    void interpolateIfPresent_returnsNullAndSkipsRecordingIfValueIsNull() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var outcome = InterpolatorUtils.interpolateIfPresent(interpolation, runtimeData, outcomes, "field", null);

        // then
        assertThat(outcome).isNull();
        assertThat(outcomes).isEmpty();
    }

    @Test
    void asStringOrNull_returnsStringValueIfOutcomePresent() {
        // given
        var outcome = new InterpolationOutcome("value", "value");

        // when / then
        assertThat(InterpolatorUtils.asStringOrNull(outcome)).isEqualTo("value");
    }

    @Test
    void asStringOrNull_returnsNullIfOutcomeIsNull() {
        // when / then
        assertThat(InterpolatorUtils.asStringOrNull(null)).isNull();
    }

    @Test
    void interpolateArgs_interpolatesEveryValue() {
        // given
        var args = new LinkedHashMap<String, String>();
        args.put("offset", "0.01");
        args.put("unit", "seconds");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var result = InterpolatorUtils.interpolateArgs(args, interpolation, runtimeData, outcomes);

        // then
        assertThat(result).containsExactly(Map.entry("offset", "0.01-resolved"), Map.entry("unit", "seconds-resolved"));
    }

    @Test
    void interpolateArgs_recordsOutcomesUnderTheArgsPrefix() {
        // given
        var args = Map.of("offset", "0.01");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        InterpolatorUtils.interpolateArgs(args, interpolation, runtimeData, outcomes);

        // then — the prefix is what keeps an argument from colliding with a field of the same name
        assertThat(outcomes).containsOnlyKeys("args.offset");
    }

    @Test
    void interpolateArgs_addsToOutcomesRatherThanReplacingThem() {
        // given — the caller has already recorded its own fields
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("a", new InterpolationOutcome("already", "already"));

        // when
        InterpolatorUtils.interpolateArgs(Map.of("offset", "0.01"), interpolation, runtimeData, outcomes);

        // then
        assertThat(outcomes).containsOnlyKeys("a", "args.offset");
    }

    @Test
    void interpolateArgs_preservesIterationOrder() {
        // given
        var args = new LinkedHashMap<String, String>();
        args.put("z", "1");
        args.put("a", "2");
        args.put("m", "3");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var result = InterpolatorUtils.interpolateArgs(args, interpolation, runtimeData, outcomes);

        // then
        assertThat(result.keySet()).containsExactly("z", "a", "m");
        assertThat(outcomes.keySet()).containsExactly("args.z", "args.a", "args.m");
    }

    @Test
    void interpolateArgs_returnsAnEmptyMapForAnEmptyBag() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var result = InterpolatorUtils.interpolateArgs(Map.of(), interpolation, runtimeData, outcomes);

        // then
        assertThat(result).isEmpty();
        assertThat(outcomes).isEmpty();
    }

    @Test
    void interpolateArgs_throwsNamingTheArgumentWithNoValue() {
        // given
        var args = new LinkedHashMap<String, String>();
        args.put("offset", null);
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when / then
        assertThatThrownBy(() -> InterpolatorUtils.interpolateArgs(args, interpolation, runtimeData, outcomes))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("offset");
    }

    @Test
    void interpolateArgs_returnsAFreshMutableMap() {
        // given
        var args = Map.of("offset", "0.01");
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        // when
        var result = InterpolatorUtils.interpolateArgs(args, interpolation, runtimeData, outcomes);
        result.put("added", "later");

        // then — the caller owns the result; the bag it was given is untouched
        assertThat(result).containsKey("added");
        assertThat(args).containsOnlyKeys("offset");
    }
}
