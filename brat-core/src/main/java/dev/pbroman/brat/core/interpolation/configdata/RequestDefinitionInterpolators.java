package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.data.ConfigData;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

/**
 * The interpolators available for request definitions, looked up by the definition's own class.
 * <p>
 * Separate from handler selection on purpose, because the two answer different questions. Which
 * handler executes a request depends on what the suite tree declares and is resolved by whatever
 * walks it; which interpolator prepares a definition depends only on the definition's <strong>type</strong>
 * and is the same for every request of that type in every suite. So this is fixed for a run and is
 * handed to a request processor once, while the handler arrives per request.
 */
@Slf4j
public final class RequestDefinitionInterpolators {

    private final Map<Class<?>, RequestDefinitionInterpolator<?>> byType;

    /**
     * Collapses the given interpolators into a registry keyed by {@code definitionType()}.
     * <p>
     * No order survives to lookup time, so two interpolators for one type can only be an overlay:
     * the <strong>last wins</strong>, logged at WARN naming both, which is what lets a consumer
     * replace a built-in interpolator with its own.
     *
     * @param interpolators the interpolators to register; never {@code null}, possibly empty
     * @throws BratException if {@code interpolators} is {@code null}, or if any element or any
     *         element's {@code definitionType()} is {@code null}
     */
    public RequestDefinitionInterpolators(List<RequestDefinitionInterpolator<?>> interpolators) {
        Require.noNullElements(interpolators, "The interpolators or any of its elements must not be null");
        var registered = new LinkedHashMap<Class<?>, RequestDefinitionInterpolator<?>>();
        for (var interpolator : interpolators) {
            var type = interpolator.definitionType();
            Require.nonNull(
                    type, "The definitionType of " + interpolator.getClass().getName() + " must not be null");
            var replaced = registered.put(type, interpolator);
            if (replaced != null) {
                log.warn(
                        "The interpolator {} for {} is replaced by {}",
                        replaced.getClass().getName(),
                        type.getName(),
                        interpolator.getClass().getName());
            }
        }
        this.byType = Map.copyOf(registered);
    }

    /**
     * Whether a definition of exactly {@code type} can be interpolated.
     *
     * @param type the definition class to look for
     * @return {@code true} if an interpolator is registered for that exact class
     */
    public boolean has(Class<?> type) {
        return byType.containsKey(type);
    }

    /**
     * Interpolates {@code definition} with the interpolator registered for its exact class.
     * <p>
     * <strong>Exact class, never a supertype.</strong> A subclass of a registered definition fails
     * here rather than resolving to its parent's interpolator, which would silently leave the
     * subclass's own fields holding raw {@code ${...}} tokens — a request that then runs, and passes.
     *
     * @param definition the authored definition to interpolate; never {@code null}
     * @param interpolation resolves the tokens
     * @param runtimeData the namespaces to resolve against
     * @return a fresh interpolated copy, as {@link RequestDefinitionInterpolator#interpolated}
     *         describes
     * @throws BratException if {@code definition} is {@code null}; if no interpolator is registered
     *         for its exact class, naming that class; if the definition is not a {@link ConfigData},
     *         which a request definition must be; or for whatever the interpolator itself rejects,
     *         an already-interpolated copy included
     */
    public RequestDefinition interpolated(
            RequestDefinition definition, Interpolation interpolation, RuntimeData runtimeData) {
        Require.nonNull(definition, "The request definition to interpolate must not be null");
        var interpolator = byType.get(definition.getClass());
        Require.nonNull(
                interpolator,
                "No interpolator is registered for the request definition "
                        + definition.getClass().getName() + ", so it cannot be interpolated");
        if (!(definition instanceof ConfigData)) {
            throw new BratException("The request definition "
                    + definition.getClass().getName() + " is not a ConfigData, so it cannot be interpolated");
        }
        return interpolate(interpolator, definition, interpolation, runtimeData);
    }

    /**
     * Names the type both casts need, which is the only reason this is a method at all.
     * <p>
     * It has one caller and exists for the type system rather than for reuse: the interpolator and
     * the definition have to meet at a type that is both {@link ConfigData} and
     * {@link RequestDefinition}, and Java has no denotable intersection type — there is no way to
     * write {@code RequestDefinitionInterpolator<ConfigData & RequestDefinition>} as a type argument.
     * A generic method's type variable is the only way to introduce one; inlining this means going raw.
     * <p>
     * The cast is unchecked because the registry's key is a {@code Class} and its value's type
     * parameter cannot be related to it in the type system; it is sound because the key came from
     * {@code definitionType()} and the lookup matched {@code definition.getClass()} exactly.
     *
     * @param interpolator the interpolator registered for {@code definition}'s class
     * @param definition the definition to interpolate
     * @param interpolation resolves the tokens
     * @param runtimeData the namespaces to resolve against
     * @param <T> the definition's own type
     * @return the interpolated copy
     */
    @SuppressWarnings("unchecked")
    private static <T extends ConfigData & RequestDefinition> RequestDefinition interpolate(
            RequestDefinitionInterpolator<?> interpolator,
            RequestDefinition definition,
            Interpolation interpolation,
            RuntimeData runtimeData) {
        return ((RequestDefinitionInterpolator<T>) interpolator)
                .interpolated((T) definition, interpolation, runtimeData);
    }
}
