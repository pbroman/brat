package dev.pbroman.brat.core.resolver.condition.rules;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static dev.pbroman.brat.core.util.Constants.EQUAL;
import static dev.pbroman.brat.core.util.Constants.PAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateConditionResolverRuleTest extends AbstractConditionResolverRuleTest {

    @BeforeEach
    void setUp() {
        resolver = new DateConditionResolverRule();
    }

    @ParameterizedTest
    @CsvSource({
        "equal,2002-12-21,2002-12-21",
        "equal,2002-12-21,21.12.2002",
        "equal,21.12.2002,12/21/2002",
        "equal,2002-12-21,12/21/2002",
        "before,2002-12-21,2002-12-22",
        "after,2002-12-22,2002-12-21",
        "past,2002-12-21,",
        "future,3002-12-21,",
        "!after,2002-12-21,2002-12-22",
        "!before,2002-12-22,2002-12-21",
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
        "isEqual,2002-12-21,2002-12-22",
        "isEqualTo,2026-11-19T10:00:00Z,2026-11-19T10:00:01Z",
        "isBefore,2002-12-22,2002-12-21",
        "isAfter,2002-12-21,2002-12-22",
        "isPast,3002-12-21,",
        "isFuture,2002-12-21,",
        "isPast,3002-12-21T10:00:00Z,",
        "isFuture,2002-12-21T10:00:00,",
    })
    void falseConditions(String func, String a, String b) {
        // when
        var result = resolver.resolve(new Condition(func, a, b));

        // then
        assertThat(result).contains(false);
    }

    @Test
    void resolve_declinesAnOperandThatIsNotADate() {
        // given
        var condition = new Condition(PAST, "20-12-23", null);

        // when
        var result = resolver.resolve(condition);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void customDateTimeFormatterIsHonored() {
        // given
        var customFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
        var resolverWithCustomFormatter = new DateConditionResolverRule(customFormatter);
        var condition = new Condition(EQUAL, "Dec 21, 2002", "Dec 21, 2002");

        // when
        var result = resolverWithCustomFormatter.resolve(condition);

        // then
        assertThat(result).contains(true);
    }

    @Test
    void customDateTimeFormatterRejectsDefaultPatterns() {
        // given
        var customFormatter = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH);
        var resolverWithCustomFormatter = new DateConditionResolverRule(customFormatter);
        var condition = new Condition(PAST, "2002-12-21", null);

        // when
        var result = resolverWithCustomFormatter.resolve(condition);

        // then
        assertThat(result).isEmpty();
    }

    // --- timestamps ---

    @ParameterizedTest
    @CsvSource({
        // with an offset or zone: compared as instants
        "isEqualTo,2026-11-19T10:00:00Z,2026-11-19T11:00:00+01:00",
        "isEqual,2026-11-19T10:00:00Z,2026-11-19T11:00:00+01:00[Europe/Berlin]",
        "isBefore,2026-11-19T10:30:00+01:00,2026-11-19T10:00:00Z",
        "isAfter,2026-11-19T10:00:00Z,2026-11-19T10:30:00+01:00",
        "isNotEqualTo,2026-11-19T10:00:00Z,2026-11-19T10:00:00+01:00",
        // local: compared as written
        "isEqualTo,2026-11-19T10:00:00,2026-11-19T10:00",
        "isBefore,2026-11-19T10:00:00,2026-11-19T10:00:01",
        "isAfter,2026-11-20T00:00:00,2026-11-19T23:59:59",
    })
    void resolve_comparesTimestamps(String func, String a, String b) {
        // when
        var result = resolver.resolve(new Condition(func, a, b));

        // then
        assertThat(result).contains(true);
    }

    @ParameterizedTest
    @CsvSource({
        "isPast,2002-12-21T10:00:00Z",
        "isFuture,3002-12-21T10:00:00+01:00",
        "isPast,2002-12-21T10:00:00",
        "isFuture,3002-12-21T10:00:00",
        "isNotPast,3002-12-21T10:00:00Z",
    })
    void resolve_comparesATimestampWithNow(String func, String a) {
        // when
        var result = resolver.resolve(new Condition(func, a, null));

        // then
        assertThat(result).contains(true);
    }

    @Test
    void resolve_comparesALocalTimestampWithNowInTheDefaultZone() {
        // given — a minute either side of the local now, wherever the JVM runs
        var now = LocalDateTime.now();

        // when / then
        assertThat(resolver.resolve(new Condition("isPast", now.minusMinutes(1).toString(), null)))
                .contains(true);
        assertThat(resolver.resolve(new Condition("isFuture", now.plusMinutes(1).toString(), null)))
                .contains(true);
    }

    @ParameterizedTest
    @CsvSource({
        "isBefore,2026-11-19,2026-11-19T10:00:00Z",
        "isEqualTo,2026-11-19T10:00:00,2026-11-19",
        "isAfter,2026-11-19T10:00:00,2026-11-19T10:00:00Z",
        "isNotEqualTo,2026-11-19T10:00:00+01:00,2026-11-19T10:00:00",
    })
    void resolve_throwsForOperandsOfDifferentKindsNamingTheFunc(String func, String a, String b) {
        // when / then
        assertThatThrownBy(() -> resolver.resolve(new Condition(func, a, b)))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'" + func.replace("Not", "") + "'")
                .hasMessageNotContaining(a)
                .hasMessageNotContaining(b);
    }

    @Test
    void resolve_declinesTextThatIsNeitherADateNorATimestamp() {
        // when
        var result = resolver.resolve(new Condition("isEqualTo", "2026-11-19T10:00:00Z", "soon"));

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_readsTimestampsWithACustomDateFormatterToo() {
        // given — the formatter is for dates; timestamps are always ISO
        var custom = new DateConditionResolverRule(DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.ENGLISH));

        // when
        var result = custom.resolve(new Condition("isBefore", "2026-11-19T10:00:00Z", "2026-11-19T10:00:01Z"));

        // then
        assertThat(result).contains(true);
    }
}
