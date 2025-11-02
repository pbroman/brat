package dev.pbroman.brat.core.resolver.condition.rules;

import static dev.pbroman.brat.core.util.Constants.BOOLEAN_CONDITION;
import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;

import java.util.List;

public class BooleanConditionResolverRule extends AbstractConditionResolverRule {

    public BooleanConditionResolverRule() {
        super();
    }

    @Override
    public String category() {
        return BOOLEAN_CONDITION;
    }

    @Override
    protected void initFunctionMap() {
        functionMap.put(TRUE, (a, b) -> parse(a));
        functionMap.put(FALSE, (a, b) -> !parse(a));
    }

    @Override
    protected List<String> ignoreBNullCheck() {
        return List.of(TRUE, FALSE);
    }

    private Boolean parse(Object value) {
        return Boolean.valueOf(String.valueOf(value));
    }

}
