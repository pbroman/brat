package dev.pbroman.brat.core.resolver.condition;

import static dev.pbroman.brat.core.util.CheckUtils.checkCondition;

import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.result.ValidationType;
import dev.pbroman.brat.core.exception.ValidationException;

public class ConditionResolverRuleDispatcher implements ConditionResolver {

    protected final List<ConditionResolverRule> resolvers;

    public ConditionResolverRuleDispatcher(List<ConditionResolverRule> resolvers) {
        this.resolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(ConditionResolverRule::priority).reversed())
                .toList();
    }

    @Override
    public Boolean resolve(Condition condition) throws ValidationException {
        checkCondition(condition);
        for (var resolver : resolvers) {
            var result = resolver.resolve(condition);
            if (result != null) {
                return result;
            }
        }
        throw new ValidationException(String.format("The '%s' could not be resolved", condition), ValidationType.FAIL);
    }
}
