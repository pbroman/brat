package dev.pbroman.brat.core.api.resolver;

/**
 * Interface for condition resolver rules, extending the {@link ConditionResolver} interface.
 */
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

    /**
     * A placeholder string for the condition function category (e.g. Number, String ...).
     *
     * @return the placeholder
     */
    String category();

}
