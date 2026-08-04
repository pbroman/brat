package dev.pbroman.brat.core.resolver.condition.rules;

import java.math.BigDecimal;
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
import static dev.pbroman.brat.core.util.Constants.ARG_MAX;
import static dev.pbroman.brat.core.util.Constants.ARG_MIN;
import static dev.pbroman.brat.core.util.Constants.CONTAINS;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_ANY_OF;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_EXACTLY;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_EXACTLY_IN_ANY_ORDER;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_KEY;
import static dev.pbroman.brat.core.util.Constants.CONTAINS_ONLY;
import static dev.pbroman.brat.core.util.Constants.DOES_NOT_HAVE_DUPLICATES;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.HAS_SIZE;
import static dev.pbroman.brat.core.util.Constants.HAS_SIZE_BETWEEN;
import static dev.pbroman.brat.core.util.Constants.HAS_SIZE_GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.JSON_CONDITION;
import static dev.pbroman.brat.core.util.Constants.SORTED;
import static dev.pbroman.brat.core.util.Constants.SORTED_DESCENDING;
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
        predicates.put(HAS_SIZE, (a, b, args) -> sizeOf(a) == size(b));
        predicates.put(HAS_SIZE_GREATER_THAN, (a, b, args) -> sizeOf(a) > size(b));
        predicates.put(HAS_SIZE_BETWEEN, (a, b, args) -> {
            rejectUnknownArgs(args, ARG_MIN, ARG_MAX);
            var size = sizeOf(a);
            return size >= size(requiredArg(args, ARG_MIN)) && size <= size(requiredArg(args, ARG_MAX));
        });
        predicates.put(DOES_NOT_HAVE_DUPLICATES, (a, b, args) -> {
            var elements = asList(a);
            return new HashSet<>(elements).size() == elements.size();
        });
        predicates.put(SORTED, (a, b, args) -> isOrdered(asList(a), true));
        predicates.put(SORTED_DESCENDING, (a, b, args) -> isOrdered(asList(a), false));
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

    /**
     * The number of elements of a sequence or entries of a mapping.
     */
    private static int sizeOf(Object value) {
        return switch (value) {
            case List<?> list -> list.size();
            case Map<?, ?> map -> map.size();
            default ->
                throw new BratException("This function needs a sequence or a mapping, but a was " + describe(value));
        };
    }

    /**
     * A size given as an operand or an argument, written either as YAML-native number or as text.
     */
    private static int size(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new BratException("A size must be a whole number, but was '" + value + "'");
        }
    }

    /**
     * Whether the elements are in order, which is vacuously true for fewer than two of them.
     */
    private static boolean isOrdered(List<?> values, boolean ascending) {
        for (var i = 1; i < values.size(); i++) {
            var comparison = compare(values.get(i - 1), values.get(i));
            if (ascending ? comparison > 0 : comparison < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Orders two JSON values.
     * <p>
     * Numbers compare numerically as exact decimals, so a sequence mixing {@code 1} and {@code 2.5}
     * — ordinary in JSON, an {@code Integer} beside a {@code Double} — orders as a reader expects
     * rather than throwing on the type difference. Anything else must be mutually {@link Comparable}.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compare(Object left, Object right) {
        if (left == null || right == null) {
            throw new BratException("A sequence holding null has no order");
        }
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return new BigDecimal(leftNumber.toString()).compareTo(new BigDecimal(rightNumber.toString()));
        }
        if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
            return comparable.compareTo(right);
        }
        throw new BratException("Cannot order " + describe(left) + " against " + describe(right));
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
     * The funcs that judge {@code a} alone. Everything else needs {@code b}, which is why
     * {@code contains} with none fails loudly instead of quietly testing against nothing.
     */
    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(HAS_SIZE_BETWEEN, DOES_NOT_HAVE_DUPLICATES, SORTED, SORTED_DESCENDING);
    }
}
