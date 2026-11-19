package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BooleanConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new BooleanConditionResolverRule();
    }

    @Test
    void isTrue() {
        // when
        var result = resolver.resolve(new Condition("isTrue", true, null));

        // then
        assertThat(result).contains(true);
    }

    @Test
    void isFalse() {
        // when
        var result = resolver.resolve(new Condition("isFalse", false, null));

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True"})
    void resolve_readsTheTextTrueInAnyCase(String a) {
        // when / then
        assertThat(resolver.resolve(new Condition("isTrue", a, null))).contains(true);
        assertThat(resolver.resolve(new Condition("isFalse", a, null))).contains(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "False"})
    void resolve_readsTheTextFalseInAnyCase(String a) {
        // when / then
        assertThat(resolver.resolve(new Condition("isFalse", a, null))).contains(true);
        assertThat(resolver.resolve(new Condition("isTrue", a, null))).contains(false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"banana", "", "yes", "1", " true"})
    void resolve_throwsForTextThatIsNotABooleanNamingTheFunc(String a) {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition("isFalse", a, null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'isFalse'");
    }

    @Test
    void resolve_throwsForANumber() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition("isTrue", 1, null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'isTrue'");
    }

    @Test
    void resolve_throwsForASequenceSayingSoButNotItsContent() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition("isFalse", List.of("s3cr3t"), null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'isFalse'")
                .hasMessageContaining("a sequence")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void resolve_throwsForAMappingSayingSoButNotItsContent() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition("isTrue", Map.of("s3cr3t", 1), null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'isTrue'")
                .hasMessageContaining("a mapping")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void resolve_negatesWhatItReads() {
        // when / then
        assertThat(resolver.resolve(new Condition("isNotTrue", "false", null))).contains(true);
        assertThat(resolver.resolve(new Condition("!isFalse", Boolean.TRUE, null)))
                .contains(true);
    }
}
