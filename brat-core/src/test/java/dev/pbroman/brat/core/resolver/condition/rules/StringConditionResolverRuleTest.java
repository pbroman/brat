package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StringConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new StringConditionResolverRule();
    }

    @ParameterizedTest
    @CsvSource({
        "equals,test,test",
        "equalsIgnoreCase,test,TEST",
        "startsWith,test,te",
        "endsWith,test,st",
        "matches,test,t.*t",
        "contains,test,es",
        "null,,",
        "empty,'',",
        "blank,' ',",
    })
    void trueConditions(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @CsvSource({
        "equals,test,other",
        "equalsIgnoreCase,test,other",
        "startsWith,test,st",
        "endsWith,test,te",
        "matches,test,T.*t",
        "contains,test,se",
        "null,' ',",
        "empty,' ',",
        "blank,test,",
    })
    void falseConditions(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(false);
    }

    @ParameterizedTest
    @CsvSource({
        "containsIgnoringCase,Content-Type,CONTENT",
        "containsIgnoringCase,content-type,Type",
        "isMediaType,application/json,application/json",
        "isMediaType,application/json; charset=utf-8,application/json",
        "isMediaType,application/json,application/json; charset=utf-8",
        "isMediaType,APPLICATION/JSON,application/json",
    })
    void resolve_isTrueForTheNewStringFuncs(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @CsvSource({
        "containsIgnoringCase,Content-Type,length",
        "isMediaType,application/json,text/plain",
        "isMediaType,application/json; charset=utf-8,application/xml",
    })
    void resolve_isFalseForTheNewStringFuncs(String func, String a, String b) {
        // given
        var condition = new Condition(func, a, b);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).contains(false);
    }

    @Test
    void resolve_wrapsAFailureThatIsNotACategoryMismatch() {
        // given — this rule owns `matches` and accepts any operands, so a malformed regex is a
        // genuine failure rather than a reason to decline the condition
        var condition = new Condition("matches", "abc", "[");

        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition)).isInstanceOf(BratException.class);
    }

    @Test
    void resolve_masksASecretWhenAPredicateFails() {
        // given - an invalid pattern makes the matches predicate throw
        var interpolated = new Condition(
                "matches",
                "s3cr3t",
                "[",
                null,
                Map.of("a", new InterpolationOutcome("s3cr3t", "${secrets.flag} → ***", true)));

        // when / then
        assertThatThrownBy(() -> resolver.resolve(interpolated))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("***")
                .hasMessageNotContaining("s3cr3t");
    }

    // --- a structured subject ---

    @Test
    void resolve_rejectsASequenceSubject() {
        // given — the text "[a, b]" would start with "[a"
        var condition = new Condition("startsWith", List.of("a", "b"), "[a");

        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("startswith")
                .hasMessageContaining("sequence");
    }

    @Test
    void resolve_rejectsAMappingSubject() {
        // given
        var condition = new Condition("isBlank", Map.of("a", 1), null);

        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("blank")
                .hasMessageContaining("mapping");
    }

    @Test
    void resolve_rejectsAStructureForANegatedFuncToo() {
        // given — negation must not turn the rejection into a passing assertion
        var condition = new Condition("isNotEmpty", List.of(), null);

        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("sequence");
    }

    @Test
    void resolve_rejectionDoesNotQuoteTheSubject() {
        // given
        var condition = new Condition("matches", List.of("s3cr3t"), ".*");

        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition))
                .isInstanceOf(BratException.class)
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void resolve_isNullIsFalseForAStructure() {
        // when / then — a structure is never null
        assertThat(resolver.resolve(new Condition("isNull", List.of(), null))).contains(false);
        assertThat(resolver.resolve(new Condition("isNotNull", Map.of(), null))).contains(true);
    }

    @Test
    void resolve_readsAStructuredBAsText() {
        // given — only a is checked; b is read as text whatever it is
        var condition = new Condition("contains", "a [x] b", List.of("x"));

        // when / then
        assertThat(resolver.resolve(condition)).contains(true);
    }
}
