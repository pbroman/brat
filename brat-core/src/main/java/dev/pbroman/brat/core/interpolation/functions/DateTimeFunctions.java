package dev.pbroman.brat.core.interpolation.functions;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.integer;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.requireArgCount;

/**
 * Functions producing the current time, or a time relative to it.
 * <p>
 * <strong>UTC is the default zone throughout</strong>, not the machine's, so a suite gives the same
 * answer on a laptop, in CI and in a container with no {@code TZ} set. Pass a zone when local time is
 * genuinely what is meant.
 *
 * <dl>
 *   <dt>{@code ${__now}}</dt>
 *   <dd>The current instant in ISO-8601 — {@code 2026-08-05T09:15:30.123Z}.</dd>
 *   <dt>{@code ${__now(pattern)}}, {@code ${__now(pattern, zone)}}</dt>
 *   <dd>The current time rendered with a {@code java.time.format.DateTimeFormatter} pattern, in UTC
 *       or in the named zone: {@code ${__now(yyyy-MM-dd HH:mm, Europe/Berlin)}}.</dd>
 *   <dt>{@code ${__epoch}}</dt>
 *   <dd>Milliseconds since 1970-01-01T00:00:00Z. No arguments.</dd>
 *   <dt>{@code ${__date(offset)}}, {@code ${__date(offset, pattern)}},
 *       {@code ${__date(offset, pattern, zone)}}</dt>
 *   <dd>The same as {@code __now}, shifted by an offset: {@code ${__date(+7d, yyyy-MM-dd)}} for a
 *       week from now. The offset is a sign, a number and a unit — <strong>{@code s} seconds,
 *       {@code m} minutes, {@code h} hours, {@code d} days, {@code w} weeks, {@code M}
 *       months</strong>. Case distinguishes minutes from months, the same way it does in a
 *       {@code DateTimeFormatter} pattern. A missing sign means {@code +}.</dd>
 * </dl>
 */
public final class DateTimeFunctions {

    private static final Pattern OFFSET = Pattern.compile("([+-]?)(\\d+)([smhdwM])");

    private DateTimeFunctions() {
        // no instances
    }

    /**
     * The date and time functions, keyed by their bare names.
     *
     * @return the functions
     */
    public static Map<String, BratFunction> functions() {
        return Map.of(
                "now", DateTimeFunctions::now,
                "epoch", DateTimeFunctions::epoch,
                "date", DateTimeFunctions::date);
    }

    private static String epoch(List<String> args) {
        requireArgCount("epoch", args, 0, 0);
        return String.valueOf(System.currentTimeMillis());
    }

    private static String now(List<String> args) {
        requireArgCount("now", args, 0, 2);
        if (args.isEmpty()) {
            return Instant.now().toString();
        }
        var zone = args.size() == 2 ? zoneOf(args.get(1)) : ZoneOffset.UTC;
        return formatterOf(args.getFirst()).format(ZonedDateTime.now(zone));
    }

    private static String date(List<String> args) {
        requireArgCount("date", args, 1, 3);
        var zone = args.size() == 3 ? zoneOf(args.get(2)) : ZoneOffset.UTC;
        var shifted = shift(ZonedDateTime.now(zone), args.getFirst());
        return args.size() == 1
                ? shifted.toInstant().toString()
                : formatterOf(args.get(1)).format(shifted);
    }

    private static ZonedDateTime shift(ZonedDateTime from, String offset) {
        var matcher = OFFSET.matcher(offset.trim());
        if (!matcher.matches()) {
            throw new BratException("'" + offset
                    + "' is not a valid offset; expected a sign, a number and one of s, m, h, d, w, M — e.g. +7d");
        }
        // integer() rather than Long.parseLong: the pattern bounds the digits' shape but not their
        // magnitude, and an overflowing count must fail as a BratException like everything else
        var amount = integer("date", matcher.group(2)) * ("-".equals(matcher.group(1)) ? -1 : 1);
        try {
            return switch (matcher.group(3)) {
                case "s" -> from.plusSeconds(amount);
                case "m" -> from.plusMinutes(amount);
                case "h" -> from.plusHours(amount);
                case "d" -> from.plusDays(amount);
                case "w" -> from.plusWeeks(amount);
                case "M" -> from.plusMonths(amount);
                // Unreachable — the pattern already restricts the unit — but a switch over a
                // String must be exhaustive, so this cannot be dropped or covered by a test.
                default -> throw new BratException("'" + offset + "' has an unknown unit");
            };
        } catch (DateTimeException | ArithmeticException e) {
            throw new BratException("'" + offset + "' shifts the time beyond what a date can hold", e);
        }
    }

    private static DateTimeFormatter formatterOf(String pattern) {
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException e) {
            throw new BratException("'" + pattern + "' is not a valid date-time pattern", e);
        }
    }

    private static ZoneId zoneOf(String zone) {
        try {
            return ZoneId.of(zone);
        } catch (RuntimeException e) {
            // DateTimeException for a malformed id, ZoneRulesException for a well-formed unknown one
            throw new BratException("'" + zone + "' is not a known time zone", e);
        }
    }
}
