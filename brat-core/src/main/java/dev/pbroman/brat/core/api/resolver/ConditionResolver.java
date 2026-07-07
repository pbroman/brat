package dev.pbroman.brat.core.api.resolver;

import dev.pbroman.brat.core.data.Condition;

/**
 * Main interface for all condition resolvers.
 */
public interface ConditionResolver {

    /**
     * Resolves a {@link Condition} testing if a func b is true.
     * <p>
     * If an implementation of the interface is not responsible for the function, it must return null.
     * </p>
     * @param condition the {@link Condition}
     * @return true, false, or null if the function cannot be resolved
     */
    Boolean resolve(Condition condition);

}