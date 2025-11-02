package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.EQUAL_TO;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN;
import static dev.pbroman.brat.core.util.Constants.GREATER_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN;
import static dev.pbroman.brat.core.util.Constants.LESS_THAN_OR_EQUAL;
import static dev.pbroman.brat.core.util.Constants.NUMBER_CONDITION;

public class NumberConditionResolverRule extends AbstractConditionResolverRule {

    public NumberConditionResolverRule() {
        super();
    }

    @Override
    public String category() {
        return NUMBER_CONDITION;
    }

    @Override
    protected void initFunctionMap() {
        functionMap.put(EQUAL_TO, (a, b) -> parse(a).equals(parse(b)));
        functionMap.put(GREATER_THAN, (a, b) -> parse(a).compareTo(parse(b)) > 0);
        functionMap.put(LESS_THAN, (a, b) -> parse(a).compareTo(parse(b)) < 0);
        functionMap.put(GREATER_THAN_OR_EQUAL, (a, b) -> parse(a).compareTo(parse(b)) >= 0);
        functionMap.put(LESS_THAN_OR_EQUAL, (a, b) -> parse(a).compareTo(parse(b)) <= 0);
    }

    private Double parse(Object value) {
        return Double.valueOf(String.valueOf(value));
    }

}
