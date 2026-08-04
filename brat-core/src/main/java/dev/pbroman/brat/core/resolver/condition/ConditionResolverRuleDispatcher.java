package dev.pbroman.brat.core.resolver.condition;

import static dev.pbroman.brat.core.util.Require.nonNull;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

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

    /**
     * Constructs a dispatcher over the rules, sorted by descending priority — which also decides
     * which rule gets first refusal on a func several categories answer to.
     *
     * @param resolvers the rules to dispatch over
     */
    public ConditionResolverRuleDispatcher(List<ConditionResolverRule> resolvers) {
        this.resolvers = resolvers.stream()
                .sorted(Comparator.comparingInt(ConditionResolverRule::priority).reversed())
                .toList();
    }

    @Override
    public boolean resolve(Condition condition) {
        nonNull(condition, "The condition may not be null");
        return resolvers.stream()
                .map(resolver -> resolver.resolve(condition))
                .flatMap(Optional::stream)
                .findFirst()
                .orElseThrow(() -> new BratException(
                        String.format("The condition '%s' could not be resolved", condition)));
    }
}