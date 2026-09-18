package dev.pbroman.brat.core.api.interpolation;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.data.ConfigData;

/**
 * Interpolates one concrete {@link RequestDefinition} type — the one branch of the
 * {@link ConfigDataInterpolator} family that is looked up rather than wired.
 * <p>
 * <strong>Why this branch alone carries a key.</strong> Every other interpolator is handed to the
 * collaborator that needs it, with its type known statically at that point; a request definition's
 * is the only one a run has to <em>find</em>, because which definition type a request holds is
 * decided by the suite document and the registered protocols. So the key lives here rather than on
 * the base interface, where it would declare an identity nothing consults.
 * <p>
 * The intersection bound is what makes "a definition is also {@link ConfigData}" a compile-time
 * requirement rather than advice: a protocol's definition has to be interpolatable, carry
 * {@code outcomes} and be copy-on-interpolate like every other authored type.
 *
 * @param <T> the concrete definition type this interpolates
 */
public interface RequestDefinitionInterpolator<T extends ConfigData & RequestDefinition>
        extends ConfigDataInterpolator<T> {

    /**
     * The class this interpolates, which is its registration key.
     *
     * @return the definition type; never {@code null}, and equal to the
     *         {@link dev.pbroman.brat.core.api.handler.RequestHandler#definitionType()} of the
     *         handlers for that protocol — a registered protocol whose definition type has no
     *         interpolator is a wiring failure, reported when the runner is built rather than on the
     *         first request. Lookup is by <strong>exact</strong> class, so a subclass of a registered
     *         definition is not a match and throws rather than silently going uninterpolated
     */
    Class<T> definitionType();
}
