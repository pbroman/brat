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
        var condition = new Condition(func, a, b);
        condition.setArgs(args);
        return condition;
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

    @Test
    void resolve_doesNotEquateANumberWithItsText() {
        // when / then — types are compared as parsed
        assertThat(resolver.resolve(condition("isEqualTo", List.of(42), List.of("42"))))
                .contains(false);
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
}
