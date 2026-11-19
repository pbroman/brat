package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The base rule's contract for predicate keys: a key is a func's canonical spelling, every spelling
 * that normalises like it reaches it, and the rule's own code sees only the canonical one.
 */
class CanonicalFuncNamesTest {

    private static final ConditionPredicate A_EQUALS_B =
            (a, b, args) -> String.valueOf(a).equals(b);

    /** A rule that records which name its hooks were called with. */
    private static final class RecordingRule extends AbstractConditionResolverRule {

        private final List<String> accepted = new ArrayList<>();

        RecordingRule(Map<String, ConditionPredicate> predicates) {
            super(predicates);
        }

        @Override
        protected String category() {
            return "Test";
        }

        @Override
        protected boolean accepts(Condition condition, String function) {
            accepted.add(function);
            return true;
        }

        @Override
        protected List<String> ignoreBNullCheck() {
            return List.of("isAlone");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"isWithin", "within", "ISWITHIN", "iswithin"})
    void constructor_keyIsReachedByEverySpellingThatNormalisesLikeIt(String spelling) {
        // given
        var rule = new RecordingRule(Map.of("isWithin", A_EQUALS_B));

        // when
        var result = rule.resolve(new Condition(spelling, "x", "x"));

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {"isNotWithin", "notWithin", "!within", "notIsWithin"})
    void constructor_keyIsReachedNegatedByEveryNegatedSpelling(String spelling) {
        // given
        var rule = new RecordingRule(Map.of("isWithin", A_EQUALS_B));

        // when
        var result = rule.resolve(new Condition(spelling, "x", "x"));

        // then
        assertThat(result).contains(false);
    }

    @Test
    void constructor_keyWithoutIsPrefixIsReachedWithOne() {
        // given
        var rule = new RecordingRule(Map.of("containsKey", A_EQUALS_B));

        // when
        var result = rule.resolve(new Condition("isContainsKey", "x", "x"));

        // then
        assertThat(result).contains(true);
    }

    @Test
    void constructor_keyWhoseIsBelongsToTheWordIsReachable() {
        // given — "island" normalises to "land", and so does the author's "island"
        var rule = new RecordingRule(Map.of("island", A_EQUALS_B));

        // when
        var result = rule.resolve(new Condition("island", "x", "x"));

        // then
        assertThat(result).contains(true);
    }

    @Test
    void constructor_ruleCodeSeesTheCanonicalNameWhateverTheAuthorWrote() {
        // given
        var rule = new RecordingRule(Map.of("isWithin", A_EQUALS_B));

        // when
        rule.resolve(new Condition("NOTWITHIN", "x", "x"));

        // then
        assertThat(rule.accepted).containsExactly("isWithin");
    }

    @Test
    void constructor_ignoreBNullCheckMatchesCanonicalNames() {
        // given
        var rule = new RecordingRule(Map.of("isAlone", (a, b, args) -> true));

        // when
        var result = rule.resolve(new Condition("alone", "x", null));

        // then
        assertThat(result).contains(true);
    }

    @Test
    void constructor_messagesNameTheCanonicalSpelling() {
        // given
        var rule = new RecordingRule(Map.of("isWithin", A_EQUALS_B));

        // when / then
        assertThatThrownBy(() -> rule.resolve(new Condition("within", "x", null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'isWithin'");
    }

    @Test
    void constructor_throwsIfTwoKeysNormaliseAlike() {
        // given
        var predicates = new LinkedHashMap<String, ConditionPredicate>();
        predicates.put("isWithin", A_EQUALS_B);
        predicates.put("within", A_EQUALS_B);

        // when / then
        assertThatThrownBy(() -> new RecordingRule(predicates))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'isWithin'")
                .hasMessageContaining("'within'");
    }

    @ParameterizedTest
    @ValueSource(strings = {"notarize", "!within", "isNotWithin"})
    void constructor_throwsIfAKeyNormalisesToANegation(String key) {
        // when / then
        assertThatThrownBy(() -> new RecordingRule(Map.of(key, A_EQUALS_B)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'" + key + "'");
    }

    @Test
    void funcs_returnsTheCanonicalKeys() {
        // given
        var rule = new RecordingRule(Map.of("isWithin", A_EQUALS_B, "containsKey", A_EQUALS_B, ">=", A_EQUALS_B));

        // when
        var funcs = rule.funcs();

        // then
        assertThat(funcs).containsExactlyInAnyOrder("isWithin", "containsKey", ">=");
    }

    @Test
    void funcs_isUnmodifiable() {
        // given
        var rule = new RecordingRule(Map.of("isWithin", A_EQUALS_B));

        // when
        Set<String> funcs = rule.funcs();

        // then
        assertThatThrownBy(() -> funcs.add("isOutside")).isInstanceOf(UnsupportedOperationException.class);
    }
}
