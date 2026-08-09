package dev.pbroman.brat.core.rendering.rules;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerboseCliOutcomeRendererRuleTest {

    VerboseCliOutcomeRendererRule underTest = new VerboseCliOutcomeRendererRule();

    @Test
    void render_returnsNullForOtherKinds() {
        assertThat(underTest.render("console", new RenderTarget(Map.of()))).isNull();
    }

    @Test
    void render_groupsByKeyPrefix() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "${x} → resolved"));
        outcomes.put(
                "header.Authorization", new InterpolationOutcome("Bearer ***", "Bearer ${secrets.token} → Bearer ***"));
        outcomes.put("auth.type", new InterpolationOutcome("none", "none"));

        // when
        var result = underTest.render("verbose-cli", new RenderTarget(outcomes));

        // then
        assertThat(result)
                .contains("url: ${x} → resolved")
                .contains("Header:")
                .contains("  Authorization: Bearer ${secrets.token} → Bearer ***")
                .contains("Auth:")
                .contains("  type: none");
    }

    @Test
    void render_headsTheSectionsWithTheLabelWhenThereIsOne() {
        // given
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        outcomes.put("url", new InterpolationOutcome("resolved", "resolved"));

        // when
        var result = underTest.render("verbose-cli", new RenderTarget("HttpRequestDefinition", outcomes));

        // then
        assertThat(result).startsWith("HttpRequestDefinition:" + System.lineSeparator());
    }

    @Test
    void render_isTheLabelAloneWhenThereAreNoOutcomes() {
        // when
        var result = underTest.render("verbose-cli", new RenderTarget("Auth", Map.of()));

        // then
        assertThat(result).isEqualTo("Auth:");
    }
}
