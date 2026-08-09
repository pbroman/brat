package dev.pbroman.brat.core.rendering;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutcomeRendererRuleDispatcherTest {

    RenderTarget target = new RenderTarget(Map.of());

    @Test
    void render_returnsFirstMatchingRuleResult() {
        // given
        OutcomeRendererRule declining = (kind, renderTarget) -> null;
        OutcomeRendererRule matching = (kind, renderTarget) -> "matched";
        var underTest = new OutcomeRendererRuleDispatcher(List.of(declining, matching));

        // when
        var result = underTest.render("any", target);

        // then
        assertThat(result).isEqualTo("matched");
    }

    @Test
    void render_higherPriorityRuleWinsOverLowerPriority() {
        // given
        OutcomeRendererRule low = new OutcomeRendererRule() {
            @Override
            public String render(String kind, RenderTarget renderTarget) {
                return "low";
            }
        };
        OutcomeRendererRule high = new OutcomeRendererRule() {
            @Override
            public int priority() {
                return 200;
            }

            @Override
            public String render(String kind, RenderTarget renderTarget) {
                return "high";
            }
        };
        var underTest = new OutcomeRendererRuleDispatcher(List.of(low, high));

        // when
        var result = underTest.render("any", target);

        // then
        assertThat(result).isEqualTo("high");
    }

    @Test
    void render_throwsIfNoRuleMatches() {
        // given
        OutcomeRendererRule declining = (kind, renderTarget) -> null;
        var underTest = new OutcomeRendererRuleDispatcher(List.of(declining));

        // when / then
        assertThatThrownBy(() -> underTest.render("any", target)).isInstanceOf(BratException.class);
    }

    @Test
    void render_throwsIfKindIsNull() {
        // given
        var underTest = new OutcomeRendererRuleDispatcher(List.of());

        // when / then
        assertThatThrownBy(() -> underTest.render(null, target)).isInstanceOf(BratException.class);
    }

    @Test
    void render_throwsIfTargetIsNull() {
        // given
        var underTest = new OutcomeRendererRuleDispatcher(List.of());

        // when / then
        assertThatThrownBy(() -> underTest.render("any", null)).isInstanceOf(BratException.class);
    }
}
