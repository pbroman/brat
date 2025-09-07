package dev.pbroman.brat.core.api.resolver;

import dev.pbroman.brat.core.data.Condition;

public interface ConditionResolverRule extends ConditionResolver {

    /**
     * Returns the priority of the resolver. Resolvers with higher priority are executed before ones with lower.
     * <p>
     * Priorities 0-100 are reserved for the core code.
     * </p>
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }

}
