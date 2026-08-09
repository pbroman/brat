package dev.pbroman.brat.core.api.rendering;

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
}
