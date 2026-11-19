package dev.pbroman.brat.core.resolver.condition.rules;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the canonical spelling of every core func, since these are what a suite author is offered for
 * completion and what core's messages name.
 */
class CoreFuncNamesTest {

    @Test
    void funcs_stringRule() {
        // when
        var funcs = new StringConditionResolverRule().funcs();

        // then
        assertThat(funcs)
                .containsExactlyInAnyOrder(
                        "isNull",
                        "isEqualTo",
                        "equals",
                        "isEqualToIgnoringCase",
                        "equalsIgnoreCase",
                        "isBlank",
                        "isEmpty",
                        "contains",
                        "containsIgnoringCase",
                        "isMediaType",
                        "startsWith",
                        "endsWith",
                        "matches",
                        "hasSize",
                        "hasSizeGreaterThan",
                        "hasSizeBetween");
    }

    @Test
    void funcs_jsonRule() {
        // when
        var funcs = new JsonConditionResolverRule().funcs();

        // then
        assertThat(funcs)
                .containsExactlyInAnyOrder(
                        "isEqualTo",
                        "contains",
                        "containsOnly",
                        "containsAnyOf",
                        "containsExactly",
                        "containsExactlyInAnyOrder",
                        "isOneOf",
                        "containsKey",
                        "hasSize",
                        "hasSizeGreaterThan",
                        "hasSizeBetween",
                        "doesNotHaveDuplicates",
                        "isEmpty",
                        "isSorted",
                        "isSortedDescending");
    }

    @Test
    void funcs_numberRule() {
        // when
        var funcs = new NumberConditionResolverRule().funcs();

        // then
        assertThat(funcs)
                .containsExactlyInAnyOrder(
                        "isEqualTo",
                        "=",
                        "isGreaterThan",
                        ">",
                        "isLessThan",
                        "<",
                        "isGreaterThanOrEqualTo",
                        ">=",
                        "isLessThanOrEqualTo",
                        "<=",
                        "isBetween",
                        "isCloseTo");
    }

    @Test
    void funcs_dateRule() {
        // when
        var funcs = new DateConditionResolverRule().funcs();

        // then
        assertThat(funcs)
                .containsExactlyInAnyOrder("isBefore", "isAfter", "isEqual", "isEqualTo", "isPast", "isFuture");
    }

    @Test
    void funcs_formatRule() {
        // when
        var funcs = new FormatConditionResolverRule().funcs();

        // then
        assertThat(funcs).containsExactlyInAnyOrder("isUuid", "isEmail", "isIsoDateTime", "isUrl");
    }

    @Test
    void funcs_booleanRule() {
        // when
        var funcs = new BooleanConditionResolverRule().funcs();

        // then
        assertThat(funcs).containsExactlyInAnyOrder("isTrue", "isFalse");
    }
}
