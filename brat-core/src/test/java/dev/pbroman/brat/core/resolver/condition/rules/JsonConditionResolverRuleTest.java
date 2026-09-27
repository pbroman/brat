package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new JsonConditionResolverRule();
    }

    private static Condition condition(String func, Object a, Object b) {
        return new Condition(func, a, b);
    }

    private static Condition withArgs(String func, Object a, Object b, Map<String, String> args) {
        return new Condition(func, a, b, args);
    }

    // --- it answers only about structures ---

    @Test
    void resolve_declinesAScalarSubject() {
        // when / then — `contains` on text belongs to the string rule
        assertThat(resolver.resolve(condition("contains", "abcdef", "cd"))).isEmpty();
        assertThat(resolver.resolve(condition("isEqualTo", "abc", "abc"))).isEmpty();
    }

    // --- deep equality ---

    @Test
    void resolve_comparesMappingsIgnoringKeyOrder() {
        // given
        var a = new LinkedHashMap<String, Object>();
        a.put("name", "John");
        a.put("city", "Berlin");
        var b = new LinkedHashMap<String, Object>();
        b.put("city", "Berlin");
        b.put("name", "John");

        // when / then
        assertThat(resolver.resolve(condition("isEqualTo", a, b))).contains(true);
    }

    @Test
    void resolve_comparesSequencesInOrder() {
        // when / then
        assertThat(resolver.resolve(condition("isEqualTo", List.of("a", "b"), List.of("a", "b"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of("a", "b"), List.of("b", "a"))))
                .contains(false);
    }

    @Test
    void resolve_comparesNestedStructures() {
        // given
        var a = Map.of("address", Map.of("street", "Main St", "city", "Berlin"));
        var b = Map.of("address", Map.of("city", "Berlin", "street", "Main St"));

        // when / then
        assertThat(resolver.resolve(condition("isEqualTo", a, b))).contains(true);
    }

    // --- equality by value: a YAML operand's scalars arrive as text ---

    @Test
    void resolve_equatesANumberWithItsText() {
        // when / then — JSON parses 42 as a number; `b: [42]` reaches the rule as text
        assertThat(resolver.resolve(condition("isEqualTo", List.of(42), List.of("42"))))
                .contains(true);
    }

    @Test
    void resolve_equatesNumbersOfTheSameValueWhateverTheirForm() {
        // when / then
        assertThat(resolver.resolve(condition("isEqualTo", List.of(2.5), List.of("2.50"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of(1L), List.of("1.0"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of(1), List.of("2"))))
                .contains(false);
    }

    @Test
    void resolve_equatesSignedAndFractionalNumbersWithTheirText() {
        // when / then - every way text can start a number
        assertThat(resolver.resolve(condition("isEqualTo", List.of(-1), List.of("-1"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of(1), List.of("+1"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of(0.5), List.of(".50"))))
                .contains(true);
    }

    @Test
    void resolve_comparesEmptyTextAsText() {
        // when / then - empty text is not a number, so it is not zero
        assertThat(resolver.resolve(condition("isEqualTo", List.of(""), List.of(""))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of(""), List.of("0"))))
                .contains(false);
    }

    @Test
    void resolve_equatesABooleanWithItsText() {
        // when / then
        assertThat(resolver.resolve(condition("isEqualTo", List.of(true), List.of("true"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isEqualTo", List.of(true), List.of("false"))))
                .contains(false);
    }

    @Test
    void resolve_comparesNestedNumbersByValue() {
        // given - a JSON object with a number, against the same object written in YAML
        var a = Map.of("id", 7, "tags", List.of(1, 2));
        var b = Map.of("id", "7", "tags", List.of("1", "2"));

        // when / then
        assertThat(resolver.resolve(condition("isEqualTo", a, b))).contains(true);
    }

    @Test
    void resolve_stillTellsDifferentTextApart() {
        // when / then - by-value equality does not blur text that does not read as a number
        assertThat(resolver.resolve(condition("isEqualTo", List.of("abc"), List.of("abd"))))
                .contains(false);
        assertThat(resolver.resolve(condition("isEqualTo", List.of("1a"), List.of("1"))))
                .contains(false);
    }

    @Test
    void resolve_everyElementFuncComparesNumbersByValue() {
        // given - the probe that found the defect: a JSON list of numbers against YAML-written b
        var ids = List.of(1, 2, 3);

        // when / then
        assertThat(resolver.resolve(condition("contains", ids, List.of("1")))).contains(true);
        assertThat(resolver.resolve(condition("containsAnyOf", ids, List.of("2", "9"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsOnly", ids, List.of("3", "2", "1"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsExactly", ids, List.of("1", "2", "3"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsExactlyInAnyOrder", ids, List.of("3", "1", "2"))))
                .contains(true);
    }

    @Test
    void resolve_keepsMappingKeysAsWritten() {
        // given - keys that read as the same number are still different keys
        var a = new LinkedHashMap<String, Object>();
        a.put("01", "a");
        a.put("1", "b");

        // when / then - canonical form must not merge them and hide the extra key
        assertThat(resolver.resolve(condition("isEqualTo", a, Map.of("1", "b"))))
                .contains(false);
    }

    @Test
    void resolve_containsKeyMatchesTheKeyAsWritten() {
        // when / then
        assertThat(resolver.resolve(condition("containsKey", Map.of("01", "x"), "01")))
                .contains(true);
        assertThat(resolver.resolve(condition("containsKey", Map.of("01", "x"), "1")))
                .contains(false);
    }

    @Test
    void resolve_comparesNumericTextByValueLikeTheScalarRules() {
        // when / then - documented consequence: a leading zero does not make a different value
        assertThat(resolver.resolve(condition("isOneOf", "01234", List.of("1234"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isOneOf", 1234, List.of("01234"))))
                .contains(true);
    }

    @Test
    void resolve_countsANumberAndItsTextAsDuplicates() {
        // when / then - one notion of equality, so duplicates agree with contains
        assertThat(resolver.resolve(condition("doesNotHaveDuplicates", List.of(1, "1"), null)))
                .contains(false);
    }

    // --- isOneOf ---

    @Test
    void resolve_isOneOfHoldsWhenTheScalarIsAnElement() {
        // when / then
        assertThat(resolver.resolve(condition("isOneOf", "admin", List.of("user", "admin"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isOneOf", "guest", List.of("user", "admin"))))
                .contains(false);
    }

    @Test
    void resolve_isOneOfIsAnsweredForAScalarSubject() {
        // when / then - the one func this rule accepts whatever a is
        assertThat(resolver.resolve(condition("isOneOf", "x", List.of("x")))).isPresent();
    }

    @Test
    void resolve_isOneOfComparesByValue() {
        // when / then - the status code case: a JSON number against YAML-written numbers
        assertThat(resolver.resolve(condition("isOneOf", 201, List.of("200", "201"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isOneOf", "200", List.of("200", "201"))))
                .contains(true);
    }

    @Test
    void resolve_isOneOfTreatsAScalarBAsASingleElement() {
        // when / then
        assertThat(resolver.resolve(condition("isOneOf", "a", "a"))).contains(true);
        assertThat(resolver.resolve(condition("isOneOf", "a", "b"))).contains(false);
    }

    @Test
    void resolve_isOneOfComparesASequenceSubjectAsOneValue() {
        // when / then - not element by element: [1, 2] is one of [[1, 2], [3]], and not of [1, 2]
        assertThat(resolver.resolve(condition("isOneOf", List.of(1, 2), List.of(List.of("1", "2"), List.of("3")))))
                .contains(true);
        assertThat(resolver.resolve(condition("isOneOf", List.of(1, 2), List.of("1", "2"))))
                .contains(false);
    }

    @Test
    void resolve_isNotOneOfIsTheNegation() {
        // when / then
        assertThat(resolver.resolve(condition("isNotOneOf", "guest", List.of("user", "admin"))))
                .contains(true);
        assertThat(resolver.resolve(condition("isNotOneOf", "admin", List.of("user", "admin"))))
                .contains(false);
    }

    @Test
    void resolve_isOneOfRejectsAnyArgument() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(withArgs("isOneOf", "a", List.of("a"), Map.of("ignore", "x"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ignore");
    }

    @Test
    void resolve_isOneOfThrowsForAMissingB() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition("isOneOf", "a", null)))
                .isInstanceOf(BratException.class);
    }

    // --- ignoring volatile fields ---

    @Test
    void resolve_ignoresTheNamedFieldsAtEveryDepth() {
        // given
        var actual = Map.of("id", "generated", "name", "John", "address", Map.of("id", "x", "city", "Berlin"));
        var expected = Map.of("name", "John", "address", Map.of("city", "Berlin"));

        // when / then
        assertThat(resolver.resolve(withArgs("isEqualTo", actual, expected, Map.of("ignore", "id"))))
                .contains(true);
    }

    @Test
    void resolve_ignoresSeveralNamedFields() {
        // given
        var actual = Map.of("id", "x", "createdAt", "now", "name", "John");
        var expected = Map.of("name", "John");

        // when / then
        assertThat(resolver.resolve(withArgs("isEqualTo", actual, expected, Map.of("ignore", "id, createdAt"))))
                .contains(true);
    }

    @Test
    void resolve_throwsForAnArgumentIsEqualToDoesNotKnow() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(
                        withArgs("isEqualTo", Map.of("a", 1), Map.of("a", 1), Map.of("ignoring", "id"))))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("ignoring");
    }

    // --- membership ---

    @Test
    void resolve_containsAnElementAndAListOfThem() {
        // when / then
        assertThat(resolver.resolve(condition("contains", List.of("admin", "user"), "admin")))
                .contains(true);
        assertThat(resolver.resolve(condition("contains", List.of("admin", "user"), List.of("admin", "user"))))
                .contains(true);
        assertThat(resolver.resolve(condition("contains", List.of("admin"), List.of("admin", "user"))))
                .contains(false);
    }

    @Test
    void resolve_containsOnlyAllowsAnyOrderButNoExtras() {
        // when / then
        assertThat(resolver.resolve(condition("containsOnly", List.of("a", "b"), List.of("b", "a"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsOnly", List.of("a", "b", "c"), List.of("a", "b"))))
                .contains(false);
    }

    @Test
    void resolve_containsOnlyIgnoresDuplicates() {
        // when / then — AssertJ's semantics, and the only thing separating this from
        // containsExactlyInAnyOrder, which counts them
        assertThat(resolver.resolve(condition("containsOnly", List.of("a", "a", "b"), List.of("a", "b"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsExactlyInAnyOrder", List.of("a", "a", "b"), List.of("a", "b"))))
                .contains(false);
    }

    @Test
    void resolve_comparesSequencesHoldingNulls() {
        // given — a JSON array may hold nulls, and frequency counting must survive them
        var withNull = java.util.Arrays.asList("x", null);
        var sameOtherOrder = java.util.Arrays.asList(null, "x");

        // when / then
        assertThat(resolver.resolve(condition("containsExactlyInAnyOrder", withNull, sameOtherOrder)))
                .contains(true);
        assertThat(resolver.resolve(
                        condition("containsExactlyInAnyOrder", withNull, java.util.Arrays.asList("x", "y"))))
                .contains(false);
    }

    @Test
    void resolve_containsAnyOfNeedsOneMatch() {
        // when / then
        assertThat(resolver.resolve(condition("containsAnyOf", List.of("a", "b"), List.of("x", "b"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsAnyOf", List.of("a", "b"), List.of("x", "y"))))
                .contains(false);
    }

    @Test
    void resolve_containsExactlyIsOrderSensitive() {
        // when / then
        assertThat(resolver.resolve(condition("containsExactly", List.of("a", "b"), List.of("a", "b"))))
                .contains(true);
        assertThat(resolver.resolve(condition("containsExactly", List.of("a", "b"), List.of("b", "a"))))
                .contains(false);
    }

    @Test
    void resolve_containsExactlyInAnyOrderCountsDuplicates() {
        // when / then
        assertThat(resolver.resolve(condition("containsExactlyInAnyOrder", List.of("a", "b"), List.of("b", "a"))))
                .contains(true);
        assertThat(resolver.resolve(
                        condition("containsExactlyInAnyOrder", List.of("a", "a", "b"), List.of("a", "b", "b"))))
                .contains(false);
    }

    @Test
    void resolve_containsKeyLooksInAMapping() {
        // when / then
        assertThat(resolver.resolve(condition("containsKey", Map.of("customerId", 1), "customerId")))
                .contains(true);
        assertThat(resolver.resolve(condition("containsKey", Map.of("customerId", 1), "other")))
                .contains(false);
    }

    @Test
    void resolve_containsKeyThrowsForASequence() {
        // when / then — the func is this rule's, so a wrong shape is an error rather than a decline
        assertThatThrownBy(() -> resolver.resolve(condition("containsKey", List.of("a"), "a")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("mapping");
    }

    // --- negation, from the shared normalisation ---

    @Test
    void resolve_negatesStructuralFuncs() {
        // when / then
        assertThat(resolver.resolve(condition("isNotEqualTo", List.of("a"), List.of("b"))))
                .contains(true);
        assertThat(resolver.resolve(condition("!contains", List.of("a"), "b"))).contains(true);
    }

    // --- size ---

    @Test
    void resolve_hasSizeCountsElementsAndEntries() {
        // when / then
        assertThat(resolver.resolve(condition("hasSize", List.of("a", "b", "c"), "3")))
                .contains(true);
        assertThat(resolver.resolve(condition("hasSize", List.of("a"), "3"))).contains(false);
        assertThat(resolver.resolve(condition("hasSize", Map.of("a", 1, "b", 2), "2")))
                .contains(true);
    }

    @Test
    void resolve_hasSizeAcceptsAYamlNativeNumber() {
        // when / then — b may arrive as an Integer rather than as text
        assertThat(resolver.resolve(condition("hasSize", List.of("a", "b"), 2))).contains(true);
    }

    @Test
    void resolve_hasSizeGreaterThan() {
        // when / then
        assertThat(resolver.resolve(condition("hasSizeGreaterThan", List.of("a", "b"), "1")))
                .contains(true);
        assertThat(resolver.resolve(condition("hasSizeGreaterThan", List.of("a"), "1")))
                .contains(false);
    }

    @Test
    void resolve_hasSizeBetweenIsInclusive() {
        // when / then
        assertThat(resolver.resolve(withArgs("hasSizeBetween", List.of("a"), null, Map.of("min", "1", "max", "3"))))
                .contains(true);
        assertThat(resolver.resolve(
                        withArgs("hasSizeBetween", List.of("a", "b", "c"), null, Map.of("min", "1", "max", "3"))))
                .contains(true);
        assertThat(resolver.resolve(
                        withArgs("hasSizeBetween", List.of("a", "b", "c", "d"), null, Map.of("min", "1", "max", "3"))))
                .contains(false);
    }

    @Test
    void resolve_throwsForASizeThatIsNotAWholeNumber() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition("hasSize", List.of("a"), "many")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("whole number");
    }

    // --- duplicates ---

    @Test
    void resolve_doesNotHaveDuplicates() {
        // when / then
        assertThat(resolver.resolve(condition("doesNotHaveDuplicates", List.of("a", "b"), null)))
                .contains(true);
        assertThat(resolver.resolve(condition("doesNotHaveDuplicates", List.of("a", "a"), null)))
                .contains(false);
    }

    // --- order ---

    @Test
    void resolve_isSortedAscendingAndDescending() {
        // when / then
        assertThat(resolver.resolve(condition("isSorted", List.of(1, 2, 3), null)))
                .contains(true);
        assertThat(resolver.resolve(condition("isSorted", List.of(3, 1, 2), null)))
                .contains(false);
        assertThat(resolver.resolve(condition("isSortedDescending", List.of(3, 2, 1), null)))
                .contains(true);
        assertThat(resolver.resolve(condition("isSortedDescending", List.of(1, 2, 3), null)))
                .contains(false);
    }

    @Test
    void resolve_sortsTextAndEqualNeighboursCount() {
        // when / then — equal neighbours do not break either order
        assertThat(resolver.resolve(condition("isSorted", List.of("apple", "banana"), null)))
                .contains(true);
        assertThat(resolver.resolve(condition("isSorted", List.of(1, 1, 2), null)))
                .contains(true);
    }

    @Test
    void resolve_ordersMixedNumberTypesNumerically() {
        // given — an Integer beside a Double is ordinary in JSON, and compareTo would throw on it
        var mixed = List.of(1, 2.5, 3);

        // when / then
        assertThat(resolver.resolve(condition("isSorted", mixed, null))).contains(true);
        assertThat(resolver.resolve(condition("isSorted", List.of(3, 2.5, 1), null)))
                .contains(false);
    }

    @Test
    void resolve_isSortedIsVacuouslyTrueForFewerThanTwoElements() {
        // when / then
        assertThat(resolver.resolve(condition("isSorted", List.of(), null))).contains(true);
        assertThat(resolver.resolve(condition("isSorted", List.of(1), null))).contains(true);
    }

    @Test
    void resolve_throwsWhenOrderingASequenceHoldingNull() {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(condition("isSorted", java.util.Arrays.asList(1, null), null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("null");
    }

    @Test
    void resolve_throwsWhenElementsCannotBeOrdered() {
        // when / then — a mapping has no natural order
        assertThatThrownBy(() -> resolver.resolve(condition("isSorted", List.of(Map.of("a", 1), Map.of("b", 2)), null)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("order");
    }
}
