package dev.pbroman.brat.core.api.reporting;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReporterContextTest {

    private final OutcomeRenderer renderer = (kind, target) -> "rendered";

    @Test
    void constructor_throwsIfArgsIsNull() {
        // when / then
        assertThatThrownBy(() -> new ReporterContext(null, renderer)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfRendererIsNull() {
        // when / then
        assertThatThrownBy(() -> new ReporterContext(Map.of(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_copiesTheArgsSoTheContextCannotChangeAfterwards() {
        // given
        var args = new HashMap<String, String>();
        args.put("detail", "all");

        // when
        var underTest = new ReporterContext(args, renderer);
        args.put("file", "out.txt");

        // then
        assertThat(underTest.args()).containsExactly(Map.entry("detail", "all"));
    }

    @Test
    void args_isUnmodifiable() {
        // given
        var underTest = new ReporterContext(Map.of("detail", "all"), renderer);

        // when / then
        assertThatThrownBy(() -> underTest.args().put("file", "out.txt"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
