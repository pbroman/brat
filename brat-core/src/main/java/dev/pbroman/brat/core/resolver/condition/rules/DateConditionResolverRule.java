package dev.pbroman.brat.core.resolver.condition.rules;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;

import static dev.pbroman.brat.core.util.Constants.AFTER;
import static dev.pbroman.brat.core.util.Constants.BEFORE;
import static dev.pbroman.brat.core.util.Constants.DATE_CONDITION;
import static dev.pbroman.brat.core.util.Constants.EQUAL;
import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.FUTURE;
import static dev.pbroman.brat.core.util.Constants.PAST;

/**
 * Core resolver for date and timestamp conditions: {@code isBefore}, {@code isAfter}, {@code isEqual}
 * and {@code isEqualTo}, {@code isPast} and {@code isFuture}.
 * <p>
 * <strong>What it reads.</strong> An operand is one of three kinds of point in time:
 * <ul>
 *   <li>a <strong>date</strong>, parsed with the rule's formatter — by default {@code yyyy-MM-dd},
 *       {@code dd.MM.yyyy} or {@code MM/dd/yyyy};</li>
 *   <li>a <strong>timestamp with an offset or zone</strong>, ISO 8601
 *       ({@code 2026-11-19T10:00:00Z}, {@code 2026-11-19T11:00:00+01:00},
 *       {@code 2026-11-19T11:00:00+01:00[Europe/Berlin]}), which is an instant;</li>
 *   <li>a <strong>local timestamp</strong>, ISO 8601 with no offset ({@code 2026-11-19T10:00:00}).</li>
 * </ul>
 * Timestamps are always ISO 8601; the formatter applies to dates only. The rule accepts a condition
 * only when {@code a}, and {@code b} if given, each read as one of these kinds, and declines it
 * otherwise, so other text falls to the rules after it.
 * <p>
 * <strong>How it compares.</strong> Two operands of the same kind compare by value: two timestamps with
 * offsets as instants, so {@code 2026-11-19T10:00:00Z} equals {@code 2026-11-19T11:00:00+01:00}; two
 * local timestamps or two dates as written. {@code isPast} and {@code isFuture} compare {@code a} with
 * now — today for a date, the current instant for a timestamp with an offset, and the current local
 * time in the JVM's default zone for a local timestamp.
 * <p>
 * Two operands of different kinds are an error, because the answer would need something the author
 * did not state: a date with any timestamp (which time of day), and a local timestamp with one that has
 * an offset (which zone). The message names the func and both kinds, not the values.
 */
public final class DateConditionResolverRule extends AbstractConditionResolverRule {

    private final DateTimeFormatter dateTimeFormatter;

    private static final String DEFAULT_DATE_FORMAT = "[yyyy-MM-dd][dd.MM.yyyy][MM/dd/yyyy]";

    /**
     * Default constructor with a default {@link DateTimeFormatter}.
     */
    public DateConditionResolverRule() {
        this(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
    }

    /**
     * Constructor receiving a {@link DateTimeFormatter}.
     *
     * @param dateTimeFormatter the formatter operands are parsed with
     */
    public DateConditionResolverRule(DateTimeFormatter dateTimeFormatter) {
        super(predicates(dateTimeFormatter));
        this.dateTimeFormatter = dateTimeFormatter;
    }

    private static Map<String, ConditionPredicate> predicates(DateTimeFormatter dateTimeFormatter) {
        var predicates = new HashMap<String, ConditionPredicate>();
        predicates.put(BEFORE, (a, b, args) -> compare(a, b, BEFORE, dateTimeFormatter) < 0);
        predicates.put(AFTER, (a, b, args) -> compare(a, b, AFTER, dateTimeFormatter) > 0);
        predicates.put(EQUAL, (a, b, args) -> compare(a, b, EQUAL, dateTimeFormatter) == 0);
        predicates.put(EQUAL_TO, (a, b, args) -> compare(a, b, EQUAL_TO, dateTimeFormatter) == 0);
        predicates.put(PAST, (a, b, args) -> compareToNow(a, dateTimeFormatter) < 0);
        predicates.put(FUTURE, (a, b, args) -> compareToNow(a, dateTimeFormatter) > 0);
        return predicates;
    }

    /**
     * Compares two operands as the class documents it.
     *
     * @param a the subject
     * @param b the operand compared with
     * @param func the func's canonical name, for the message
     * @param dateTimeFormatter the formatter dates are parsed with
     * @return negative, zero or positive as {@code a} is before, equal to or after {@code b}
     * @throws BratException if the two are of different kinds
     */
    private static int compare(Object a, Object b, String func, DateTimeFormatter dateTimeFormatter) {
        var left = pointInTime(a, dateTimeFormatter);
        var right = pointInTime(b, dateTimeFormatter);
        return switch (left) {
            case LocalDate date when right instanceof LocalDate other -> date.compareTo(other);
            case Instant instant when right instanceof Instant other -> instant.compareTo(other);
            case LocalDateTime local when right instanceof LocalDateTime other -> local.compareTo(other);
            default ->
                throw new BratException(StringUtils.capitalize(subject(DATE_CONDITION, func)) + " cannot compare "
                        + kindOf(left) + " with " + kindOf(right) + ": " + whatIsMissing(left, right));
        };
    }

    /**
     * Compares an operand with now, as the class documents it.
     *
     * @param a the subject, which {@link #accepts} has already read as a point in time
     * @param dateTimeFormatter the formatter dates are parsed with
     * @return negative, zero or positive as {@code a} is before, at or after now
     */
    private static int compareToNow(Object a, DateTimeFormatter dateTimeFormatter) {
        var point = pointInTime(a, dateTimeFormatter);
        if (point instanceof LocalDate date) {
            return date.compareTo(LocalDate.now());
        }
        if (point instanceof Instant instant) {
            return instant.compareTo(Instant.now());
        }
        return ((LocalDateTime) point).compareTo(LocalDateTime.now());
    }

    /**
     * Whether {@code value} reads as a date, a timestamp with an offset or zone, or a local timestamp.
     *
     * @param value an operand
     * @param dateTimeFormatter the formatter dates are parsed with
     * @return {@code true} if it reads as one of the three
     */
    private static boolean isPointInTime(Object value, DateTimeFormatter dateTimeFormatter) {
        return pointInTime(value, dateTimeFormatter) != null;
    }

    /**
     * Reads {@code value} as a {@link LocalDate}, an {@link Instant} (a timestamp with an offset or
     * zone) or a {@link LocalDateTime} (one without).
     *
     * @return the point in time, or {@code null} if {@code value} reads as none of them
     */
    private static Object pointInTime(Object value, DateTimeFormatter dateTimeFormatter) {
        var text = String.valueOf(value).trim();
        try {
            return LocalDate.parse(text, dateTimeFormatter);
        } catch (DateTimeParseException e) {
            // not a date; try a timestamp
        }
        try {
            // A ZonedDateTime takes an offset as readily as a zone, so these two cover every timestamp.
            var parsed = DateTimeFormatter.ISO_DATE_TIME.parseBest(text, ZonedDateTime::from, LocalDateTime::from);
            return parsed instanceof ZonedDateTime zoned ? zoned.toInstant() : parsed;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String kindOf(Object pointInTime) {
        return switch (pointInTime) {
            case LocalDate date -> "a date";
            case Instant instant -> "a timestamp with an offset";
            default -> "a timestamp without an offset";
        };
    }

    private static String whatIsMissing(Object left, Object right) {
        return left instanceof LocalDate || right instanceof LocalDate
                ? "which time of day the date means is not stated"
                : "which time zone the timestamp without an offset is in is not stated";
    }

    @Override
    protected String category() {
        return DATE_CONDITION;
    }

    @Override
    public int priority() {
        return 30;
    }

    @Override
    protected boolean accepts(Condition condition, String function) {
        return isPointInTime(condition.getA(), dateTimeFormatter)
                && (condition.getB() == null || isPointInTime(condition.getB(), dateTimeFormatter));
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(PAST, FUTURE);
    }
}
