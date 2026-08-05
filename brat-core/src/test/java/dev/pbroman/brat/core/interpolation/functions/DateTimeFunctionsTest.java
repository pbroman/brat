package dev.pbroman.brat.core.interpolation.functions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateTimeFunctionsTest {

    private static String call(String name, String... args) {
        return DateTimeFunctions.functions().get(name).apply(List.of(args));
    }

    // --- now ---

    @Test
    void now_returnsTheCurrentInstantInIso8601WhenGivenNoPattern() {
        // when
        var result = call("now");

        // then — parseable as an instant, and UTC-marked
        assertThat(Instant.parse(result)).isNotNull();
        assertThat(result).endsWith("Z");
    }

    @Test
    void now_formatsWithTheGivenPattern() {
        // when
        var result = call("now", "yyyy");

        // then
        assertThat(result).isEqualTo(String.valueOf(Year.now(ZoneOffset.UTC).getValue()));
    }

    @Test
    void now_formatsInUtcWhenGivenNoZone() {
        // when — VV renders the zone itself, so this pins the zone without depending on the clock
        var result = call("now", "VV");

        // then — the zone id of ZoneOffset.UTC
        assertThat(result).isEqualTo("Z");
    }

    @Test
    void now_formatsInTheGivenZone() {
        // when
        var result = call("now", "VV", "Europe/Berlin");

        // then
        assertThat(result).isEqualTo("Europe/Berlin");
    }

    @Test
    void now_throwsForAnInvalidPattern() {
        // when / then — an unknown pattern letter
        assertThatThrownBy(() -> call("now", "yyyy-QQQQQQ")).isInstanceOf(BratException.class);
    }

    @Test
    void now_throwsForAnUnknownZone() {
        // when / then
        assertThatThrownBy(() -> call("now", "yyyy", "Mars/Olympus"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Mars/Olympus");
    }

    @Test
    void now_throwsForTooManyArguments() {
        // when / then
        assertThatThrownBy(() -> call("now", "yyyy", "UTC", "extra")).isInstanceOf(BratException.class);
    }

    // --- epoch ---

    @Test
    void epoch_returnsMillisecondsSinceTheEpoch() {
        // given
        var before = System.currentTimeMillis();

        // when
        var result = call("epoch");

        // then
        assertThat(Long.parseLong(result)).isBetween(before, System.currentTimeMillis());
    }

    @Test
    void epoch_throwsForAnyArgument() {
        // when / then
        assertThatThrownBy(() -> call("epoch", "x")).isInstanceOf(BratException.class);
    }

    // --- date ---

    @Test
    void date_shiftsForwardByTheOffset() {
        // when
        var result = LocalDate.parse(call("date", "+7d", "yyyy-MM-dd"));

        // then
        assertThat(result).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(7));
    }

    @Test
    void date_shiftsBackwardForANegativeOffset() {
        // when
        var result = LocalDate.parse(call("date", "-1d", "yyyy-MM-dd"));

        // then
        assertThat(result).isEqualTo(LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    @Test
    void date_treatsAMissingSignAsForward() {
        // when
        var result = LocalDate.parse(call("date", "2w", "yyyy-MM-dd"));

        // then
        assertThat(result).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusWeeks(2));
    }

    @Test
    void date_shiftsBySeconds() {
        // given
        var now = Instant.now();

        // when
        var result = Instant.parse(call("date", "+30s"));

        // then
        assertThat(ChronoUnit.SECONDS.between(now, result)).isBetween(29L, 30L);
    }

    @Test
    void date_readsLowerCaseMAsMinutesAndUpperCaseMAsMonths() {
        // given — the one ambiguity the catalogue left open, resolved by case
        var now = Instant.now();

        // when
        var minutes = Instant.parse(call("date", "+90m"));
        var months = LocalDate.parse(call("date", "+3M", "yyyy-MM-dd"));

        // then
        assertThat(ChronoUnit.MINUTES.between(now, minutes)).isBetween(89L, 90L);
        assertThat(months).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusMonths(3));
    }

    @Test
    void date_returnsAnIso8601InstantWhenGivenNoPattern() {
        // when
        var result = call("date", "+1h");

        // then — the same shape as a bare ${__now}, so the two compose
        assertThat(Instant.parse(result)).isNotNull();
        assertThat(result).endsWith("Z");
    }

    @Test
    void date_formatsInTheGivenZone() {
        // when
        var result = call("date", "+0d", "VV", "Europe/Berlin");

        // then
        assertThat(result).isEqualTo("Europe/Berlin");
    }

    @Test
    void date_throwsForAnUnparseableOffset() {
        // when / then
        assertThatThrownBy(() -> call("date", "next tuesday"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("next tuesday");
    }

    @Test
    void date_throwsForAnUnknownOffsetUnit() {
        // when / then — years are not a supported unit
        assertThatThrownBy(() -> call("date", "+1y")).isInstanceOf(BratException.class);
    }

    @Test
    void date_throwsForAnOffsetTooLargeToParse() {
        // when / then — the pattern bounds the digits' shape but not their magnitude
        assertThatThrownBy(() -> call("date", "+99999999999999999999d")).isInstanceOf(BratException.class);
    }

    @Test
    void date_throwsForAnOffsetThatOverflowsTheCalendar() {
        // when / then — parses as a long, but the year is far past what a date can hold. Note the
        // magnitude needed: LocalDate reaches year 999999999, so ten billion days is still valid
        assertThatThrownBy(() -> call("date", "+999999999999999999d")).isInstanceOf(BratException.class);
    }

    @Test
    void date_acceptsAnOffsetOfMillionsOfYears() {
        // when — inside LocalDate's range, so it must not be rejected as overflow
        var result = call("date", "+9999999999d", "yyyy");

        // then
        assertThat(Long.parseLong(result)).isGreaterThan(20000000L);
    }

    @Test
    void date_throwsWithNoArguments() {
        // when / then
        assertThatThrownBy(() -> call("date")).isInstanceOf(BratException.class);
    }
}
