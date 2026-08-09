package dev.pbroman.brat.core.rendering.rules;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnittestOutcomeRendererRuleTest {

    UnittestOutcomeRendererRule underTest = new UnittestOutcomeRendererRule();

    @Test
    void render_returnsNullForOtherKinds() {
        assertThat(underTest.render("console", new RenderTarget(Map.of()))).isNull();
    }

    @Test
    void render_isBracketedCommaSeparated() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // when
        var result = underTest.render("unittest", new RenderTarget(outcomes));

        // then
        assertThat(result).isEqualTo("[url=${x} → resolved, method=GET]");
    }

    @Test
    void render_prefixesTheLabelWhenThereIsOne() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "resolved"));

        // when
        var result = underTest.render("unittest", new RenderTarget("HttpRequestDefinition", outcomes));

        // then
        assertThat(result).isEqualTo("HttpRequestDefinition[url=resolved]");
    }

    @Test
    void render_isEmptyBracketsWhenThereAreNoOutcomes() {
        // when
        var result = underTest.render("unittest", new RenderTarget("Auth", Map.of()));

        // then
        assertThat(result).isEqualTo("Auth[]");
    }
}
