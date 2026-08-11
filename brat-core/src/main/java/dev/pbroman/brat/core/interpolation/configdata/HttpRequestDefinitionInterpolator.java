package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateMapWithOutcomes;

/**
 * Interpolates every field of an {@link HttpRequestDefinition}.
 */
public final class HttpRequestDefinitionInterpolator implements ConfigDataInterpolator<HttpRequestDefinition> {

    private final ConfigDataInterpolator<Auth> authInterpolation;

    /**
     * Constructs an interpolator delegating the nested {@link Auth}.
     *
     * @param authInterpolation the interpolator this delegates the nested {@link Auth} to
     */
    public HttpRequestDefinitionInterpolator(ConfigDataInterpolator<Auth> authInterpolation) {
        this.authInterpolation = authInterpolation;
    }

    /**
     * Interpolates the request's {@code url}, {@code method}, {@code timeout}, every {@code body} and
     * {@code header} value, and the nested {@code auth} block.
     * <p>
     * Outcome keys are {@code url}, {@code method}, {@code timeout}, then {@code body.<key>},
     * {@code header.<name>} and {@code auth.<field>} — a nested block is flattened into this map with
     * a prefix rather than nested, so a renderer sees one flat set of keys. Header names keep the
     * author's own capitalisation, since that is what a report should echo.
     * <p>
     * {@code url} and {@code method} are always interpolated and always recorded; everything else is
     * optional. A {@code null} {@code auth} is legal and yields no {@code auth.*} outcomes and a
     * {@code null} on the copy.
     *
     * @param target the request definition to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @return a new request definition with every declared field interpolated, whose {@code body}
     *         carries the {@code _bodyString} its constructor derives from the interpolated values
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy, or if the interpolated headers contain two names differing only in
     *         case
     */
    @Override
    public HttpRequestDefinition interpolated(
            HttpRequestDefinition target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        var urlOutcome = interpolation.outcome(target.getUrl(), runtimeData);
        outcomes.put("url", urlOutcome);

        var methodOutcome = interpolation.outcome(target.getMethod(), runtimeData);
        outcomes.put("method", methodOutcome);

        var timeoutOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "timeout", target.getTimeout());

        var bodyOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getBody());
        putPrefixed(outcomes, "body.", bodyOutcomes);

        var headerOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getHeaders());
        putPrefixed(outcomes, "header.", headerOutcomes);

        // A null auth is legal — the constructor's Javadoc permits it — and yields no auth.* outcomes
        // and a null on the copy, which is what every other optional field already does.
        Auth interpolatedAuth = null;
        if (target.getAuth() != null) {
            interpolatedAuth = authInterpolation.interpolated(target.getAuth(), interpolation, runtimeData);
            putPrefixed(outcomes, "auth.", interpolatedAuth.getOutcomes());
        }

        return new HttpRequestDefinition(
                urlOutcome.asString(),
                methodOutcome.asString(),
                asStringOrNull(timeoutOutcome),
                resolveOrNull(target.getBody(), bodyOutcomes),
                resolveOrNull(target.getHeaders(), headerOutcomes),
                interpolatedAuth,
                outcomes);
    }

    /**
     * Copies every outcome into {@code outcomes} under a prefixed key, flattening a nested block into
     * its parent rather than nesting it.
     *
     * @param outcomes the map to copy into, mutated
     * @param prefix the key prefix, including its trailing dot
     * @param source the outcomes to copy
     */
    private static void putPrefixed(
            Map<String, InterpolationOutcome> outcomes, String prefix, Map<String, InterpolationOutcome> source) {
        for (var entry : source.entrySet()) {
            outcomes.put(prefix + entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, String> resolveOrNull(
            Map<String, String> original, Map<String, InterpolationOutcome> outcomes) {
        if (original == null) {
            return null;
        }
        var result = new LinkedHashMap<String, String>();
        for (var entry : outcomes.entrySet()) {
            result.put(entry.getKey(), entry.getValue().asString());
        }
        return result;
    }
}
