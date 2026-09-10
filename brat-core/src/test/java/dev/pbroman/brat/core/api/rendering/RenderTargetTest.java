package dev.pbroman.brat.core.api.rendering;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenderTargetTest {

    @Test
    void constructor_throwsIfOutcomesIsNull() {
        // when / then
        assertThatThrownBy(() -> new RenderTarget("Auth", null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_defaultsLabelToNull() {
        // given
        var outcomes = Map.of("url", new InterpolationOutcome("resolved", "resolved"));

        // when
        var underTest = new RenderTarget(outcomes);

        // then
        assertThat(underTest.label()).isNull();
        assertThat(underTest.outcomes()).isEqualTo(outcomes);
    }

    @Test
    void constructor_keepsAnEmptyOutcomesMap() {
        // when
        var underTest = new RenderTarget("Auth", Map.of());

        // then
        assertThat(underTest.label()).isEqualTo("Auth");
        assertThat(underTest.outcomes()).isEmpty();
    }

    @Test
    void constructor_copiesTheOutcomesItWasGiven() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("value", "reported"));
        var target = new RenderTarget("label", outcomes);

        // when
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // then - a renderer sees what it was constructed with, not what the caller did next
        assertThat(target.outcomes()).containsOnlyKeys("url");
    }

    @Test
    void outcomes_areUnmodifiable() {
        // given
        var target = new RenderTarget(Map.of("url", new InterpolationOutcome("value", "reported")));

        // when / then - no rule can mutate what it was handed
        assertThatThrownBy(() -> target.outcomes().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
