package dev.pbroman.brat.core.resolver.condition.rules;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static dev.pbroman.brat.core.util.Constants.AFTER;
import static dev.pbroman.brat.core.util.Constants.BEFORE;
import static dev.pbroman.brat.core.util.Constants.DATE_CONDITION;
import static dev.pbroman.brat.core.util.Constants.EQUAL;
import static dev.pbroman.brat.core.util.Constants.FUTURE;
import static dev.pbroman.brat.core.util.Constants.PAST;

/**
 * Core resolver for date conditions.
 */
public final class DateConditionResolverRule extends AbstractConditionResolverRule {

    private static final String DEFAULT_DATE_FORMAT = "[yyyy-MM-dd][dd.MM.yyyy][MM/dd/yyyy]";

    /**
     * Default constructor with a default {@link DateTimeFormatter}.
     */
    public DateConditionResolverRule() {
        this(DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT));
    }

    /**
     * Constructor receiving a {@link DateTimeFormatter}.
     */
    public DateConditionResolverRule(DateTimeFormatter dateTimeFormatter) {
        super(predicates(dateTimeFormatter));
    }

    private static Map<String, BiPredicate<Object, Object>> predicates(DateTimeFormatter dateTimeFormatter) {
        var predicates = new HashMap<String, BiPredicate<Object, Object>>();
        predicates.put(BEFORE, (a, b) -> parse(a, dateTimeFormatter).isBefore(parse(b, dateTimeFormatter)));
        predicates.put(AFTER, (a, b) -> parse(a, dateTimeFormatter).isAfter(parse(b, dateTimeFormatter)));
        predicates.put(EQUAL, (a, b) -> parse(a, dateTimeFormatter).isEqual(parse(b, dateTimeFormatter)));
        predicates.put(PAST, (a, b) -> parse(a, dateTimeFormatter).isBefore(LocalDate.now()));
        predicates.put(FUTURE, (a, b) -> parse(a, dateTimeFormatter).isAfter(LocalDate.now()));
        return predicates;
    }

    private static LocalDate parse(Object value, DateTimeFormatter dateTimeFormatter) {
        return LocalDate.parse(String.valueOf(value), dateTimeFormatter);
    }

    @Override
    public String category() {
        return DATE_CONDITION;
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(PAST, FUTURE);
    }

}
