package dev.pbroman.brat.core.resolver.condition.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Constants.ARG_IGNORE;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_ANY_OF;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_EXACTLY;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_EXACTLY_IN_ANY_ORDER;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_KEY;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_ONLY;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.JSON_CONDITION;
import static java.util.stream.Collectors.toSet;

/**
 * Core resolver for conditions over structured values — the mappings and sequences a JSONPath
 * expression or a YAML-native operand produces.
 * <p>
 * It answers only when {@code a} is a {@link Map} or a {@link List}, which is what lets it share func
 * names with the string rule: {@code contains} on text is the string rule's, {@code contains} on a
 * sequence is this one's, and the operand decides. Its priority sits above the string rule so it is
 * offered those conditions first.
 * <p>
 * Comparison is by value, using the structures' own {@code equals}: a mapping ignores key order, a
 * sequence does not. Types must match as parsed — JSON {@code 42} is an {@code Integer} and does not
 * equal the text {@code "42"}.
 */
public final class JsonConditionResolverRule extends AbstractConditionResolverRule {

    /**
     * Constructs the JSON condition rule with its predicates.
     */
    public JsonConditionResolverRule() {
        super(predicates());
    }

    private static Map<String, ConditionPredicate> predicates() {
        var predicates = new HashMap<String, ConditionPredicate>();
        predicates.put(EQUAL_TO, (a, b, args) -> {
            rejectUnknownArgs(args, ARG_IGNORE);
            var ignored = ignoredKeys(args);
            return Objects.equals(withoutIgnored(a, ignored), withoutIgnored(b, ignored));
        });
        predicates.put(CONTAINS, (a, b, args) -> asList(a).containsAll(asList(b)));
        predicates.put(CONTAINS_ONLY, (a, b, args) -> new HashSet<>(asList(a)).equals(new HashSet<>(asList(b))));
        predicates.put(CONTAINS_ANY_OF, (a, b, args) -> asList(a).stream().anyMatch(asList(b)::contains));
        predicates.put(CONTAINS_EXACTLY, (a, b, args) -> asList(a).equals(asList(b)));
        predicates.put(CONTAINS_EXACTLY_IN_ANY_ORDER, (a, b, args) -> sameElements(asList(a), asList(b)));
        predicates.put(CONTAINS_KEY, (a, b, args) -> asMap(a).containsKey(b));
        return predicates;
    }

    /**
     * The elements to compare against: a sequence as it stands, anything else as a single element,
     * so {@code b: "admin"} and {@code b: [admin, user]} both read naturally.
     */
    private static List<?> asList(Object value) {
        return switch (value) {
            case null -> List.of();
            case List<?> list -> list;
            default -> List.of(value);
        };
    }

    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        throw new BratException("This function needs a mapping, but a was " + describe(value));
    }

    /**
     * Same elements in any order, counting duplicates — so {@code [a, a, b]} does not match
     * {@code [a, b, b]}. A frequency map rather than sorting, since JSON values need not be
     * comparable.
     */
    private static boolean sameElements(List<?> actual, List<?> expected) {
        return actual.size() == expected.size() && frequencies(actual).equals(frequencies(expected));
    }

    private static Map<Object, Long> frequencies(List<?> values) {
        // Counted by hand rather than with groupingBy: that collector rejects a null key, and a
        // JSON array may hold nulls.
        var counts = new HashMap<Object, Long>();
        for (var value : values) {
            counts.merge(value, 1L, Long::sum);
        }
        return counts;
    }

    private static Set<String> ignoredKeys(Map<String, String> args) {
        var ignore = args.get(ARG_IGNORE);
        if (ignore == null) {
            return Set.of();
        }
        return Arrays.stream(ignore.split(",")).map(String::trim).collect(toSet());
    }

    /**
     * A copy with the named keys removed at every depth, so a comparison can skip the fields that
     * change on every run — generated ids, timestamps.
     */
    private static Object withoutIgnored(Object value, Set<String> ignored) {
        if (ignored.isEmpty()) {
            return value;
        }
        return switch (value) {
            case Map<?, ?> map -> {
                var kept = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    if (!ignored.contains(String.valueOf(entry.getKey()))) {
                        kept.put(entry.getKey(), withoutIgnored(entry.getValue(), ignored));
                    }
                }
                yield kept;
            }
            case List<?> list -> {
                var kept = new ArrayList<>();
                for (var element : list) {
                    kept.add(withoutIgnored(element, ignored));
                }
                yield kept;
            }
            case null, default -> value;
        };
    }

    private static String describe(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    @Override
    public String category() {
        return JSON_CONDITION;
    }

    /**
     * Above the string rule, so a func both answer to is offered here first; below the number and
     * date rules, which decline a structure anyway.
     */
    @Override
    public int priority() {
        return 15;
    }

    /**
     * Only structures are this rule's: {@code contains} on a {@link String} belongs to the string
     * rule, and declining is what routes it there.
     */
    @Override
    protected boolean accepts(Condition condition) {
        return condition.getA() instanceof Map || condition.getA() instanceof List;
    }

    /**
     * Every func here needs {@code b}, so none is exempt from the null check — which is why
     * {@code contains} with no {@code b} fails loudly instead of quietly testing against nothing.
     */
    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of();
    }
}
