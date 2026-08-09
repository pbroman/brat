package dev.pbroman.brat.core.rendering.rules;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConsoleOutcomeRendererRuleTest {

    ConsoleOutcomeRendererRule underTest = new ConsoleOutcomeRendererRule();

    @Test
    void render_returnsNullForOtherKinds() {
        assertThat(underTest.render("log", new RenderTarget(Map.of()))).isNull();
    }

    @Test
    void render_isOneLinePerOutcome() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // when
        var result = underTest.render("console", new RenderTarget(outcomes));

        // then
        assertThat(result).isEqualTo("url: ${x} → resolved" + System.lineSeparator() + "method: GET");
    }

    @Test
    void render_headsTheLinesWithTheLabelWhenThereIsOne() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("type", new InterpolationOutcome("basic", "basic"));

        // when
        var result = underTest.render("console", new RenderTarget("Auth", outcomes));

        // then
        assertThat(result).isEqualTo("Auth:" + System.lineSeparator() + "type: basic");
    }

    @Test
    void render_isTheLabelAloneWhenThereAreNoOutcomes() {
        // when
        var result = underTest.render("console", new RenderTarget("Auth", Map.of()));

        // then
        assertThat(result).isEqualTo("Auth:");
    }

    @Test
    void render_isEmptyWhenThereIsNeitherLabelNorOutcomes() {
        // when
        var result = underTest.render("console", new RenderTarget(Map.of()));

        // then
        assertThat(result).isEmpty();
    }
}
