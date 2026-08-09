package dev.pbroman.brat.core.rendering.rules;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogOutcomeRendererRuleTest {

    LogOutcomeRendererRule underTest = new LogOutcomeRendererRule();

    @Test
    void render_returnsNullForOtherKinds() {
        assertThat(underTest.render("console", new RenderTarget(Map.of()))).isNull();
    }

    @Test
    void render_isLogfmtSingleLine() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // when
        var result = underTest.render("log", new RenderTarget(outcomes));

        // then
        assertThat(result).isEqualTo("url=\"${x} → resolved\" method=\"GET\"");
    }

    @Test
    void render_escapesQuotesInValues() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("body", new InterpolationOutcome("{\"a\":1}", "{\"a\":1}"));

        // when
        var result = underTest.render("log", new RenderTarget(outcomes));

        // then
        assertThat(result).isEqualTo("body=\"{\\\"a\\\":1}\"");
    }

    @Test
    void render_escapesBackslashesInValues() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("path", new InterpolationOutcome("C:\\path\\", "C:\\path\\"));

        // when
        var result = underTest.render("log", new RenderTarget(outcomes));

        // then the trailing backslash is doubled, so a parser reads it as an escaped backslash
        // followed by the real closing quote, not as an escaped quote
        assertThat(result).isEqualTo("path=\"C:\\\\path\\\\\"");
    }

    @Test
    void render_emitsTheLabelAsALeadingPairWhenThereIsOne() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("method", new InterpolationOutcome("GET", "GET"));

        // when
        var result = underTest.render("log", new RenderTarget("HttpRequestDefinition", outcomes));

        // then
        assertThat(result).isEqualTo("_label=\"HttpRequestDefinition\" method=\"GET\"");
    }

    @Test
    void render_keepsTheLabelPairOutOfTheOutcomeKeySpace() {
        // given an outcome named after the label key, which must not collide with it
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("label", new InterpolationOutcome("mine", "mine"));

        // when
        var result = underTest.render("log", new RenderTarget("Auth", outcomes));

        // then
        assertThat(result).isEqualTo("_label=\"Auth\" label=\"mine\"");
    }

    @Test
    void render_isTheLabelPairAloneWhenThereAreNoOutcomes() {
        // when
        var result = underTest.render("log", new RenderTarget("Auth", Map.of()));

        // then
        assertThat(result).isEqualTo("_label=\"Auth\"");
    }
}
