package dev.pbroman.brat.core.resolver.condition;

import static dev.pbroman.brat.core.util.CheckUtils.checkCondition;

import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.api.resolver.ConditionResolverRule;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Dispatcher of {@link ConditionResolverRule condition resolver rules}.
 * <p>
 * The dispatcher receives a list of {@link ConditionResolverRule condition resolver rules}, sorts them according to
 * priority and returns the result of the first matching resolver. If none is found, a {@link BratException} is thrown.
 */
public class ConditionResolverRuleDispatcher implements ConditionResolver {

    protected final List<ConditionResolverRule> resolvers;

    public ConditionResolverRuleDispatcher(List<ConditionResolverRule> resolvers) {
        this.resolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(ConditionResolverRule::priority).reversed())
                .toList();
    }

    @Override
    public Boolean resolve(Condition condition) {
        checkCondition(condition);
        for (var resolver : resolvers) {
            var result = resolver.resolve(condition);
            if (result != null) {
                return result;
            }
        }
        throw new BratException(String.format("The condition '%s' could not be resolved", condition));
    }
}