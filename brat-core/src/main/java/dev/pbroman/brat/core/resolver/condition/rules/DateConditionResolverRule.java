package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.AFTER;
import static dev.pbroman.brat.core.util.Constants.BEFORE;
import static dev.pbroman.brat.core.util.Constants.DATE_CONDITION;
import static dev.pbroman.brat.core.util.Constants.EQUAL;
import static dev.pbroman.brat.core.util.Constants.FUTURE;
import static dev.pbroman.brat.core.util.Constants.PAST;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

public class DateConditionResolverRule extends AbstractConditionResolverRule {

    private final DateTimeFormatter dateTimeFormatter;

    public DateConditionResolverRule() {
        super();
        var formatterBuilder = new DateTimeFormatterBuilder()
                .append(DateTimeFormatter.ofPattern("[yyyy-MM-dd][dd.MM.yyyy][MM/dd/yyyy]")); // TODO make configurable
        this.dateTimeFormatter = formatterBuilder.toFormatter();
    }

    @Override
    public String category() {
        return DATE_CONDITION;
    }

    @Override
    protected void initPredicateMap(Map<String, BiPredicate<Object, Object>> predicateMap) {
        predicateMap.put(BEFORE, (a, b) -> parse(a).isBefore(parse(b)));
        predicateMap.put(AFTER, (a, b) -> parse(a).isAfter(parse(b)));
        predicateMap.put(EQUAL, (a, b) -> parse(a).isEqual(parse(b)));
        predicateMap.put(PAST, (a, b) -> parse(a).isBefore(LocalDate.now()));
        predicateMap.put(FUTURE, (a, b) -> parse(a).isAfter(LocalDate.now()));
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(PAST, FUTURE);
    }

    private LocalDate parse(Object value) {
        return LocalDate.parse(String.valueOf(value), dateTimeFormatter);
    }

}
