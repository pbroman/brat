package dev.pbroman.brat.core.data;

import static dev.pbroman.brat.core.util.ConfigDataInterpolationUtils.asStringOrNull;
import static dev.pbroman.brat.core.util.ConfigDataInterpolationUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.util.ConfigDataInterpolationUtils.interpolateIfPresent;
import static dev.pbroman.brat.core.util.ConfigDataInterpolationUtils.interpolateMapWithOutcomes;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.data.ConfigDataInterpolation;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

/**
 * Interpolates every field of an {@link HttpRequestDefinition}.
 */
public final class HttpRequestDefinitionInterpolation implements ConfigDataInterpolation<HttpRequestDefinition> {

    private final ConfigDataInterpolation<Auth> authInterpolation;

    public HttpRequestDefinitionInterpolation(ConfigDataInterpolation<Auth> authInterpolation) {
        this.authInterpolation = authInterpolation;
    }

    @Override
    public HttpRequestDefinition interpolated(HttpRequestDefinition target, Interpolation interpolation, RuntimeData runtimeData) {
        checkNotInterpolated(target);

        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();

        var urlOutcome = interpolation.outcome(target.getUrl(), runtimeData);
        outcomes.put("url", urlOutcome);

        var methodOutcome = interpolation.outcome(target.getMethod(), runtimeData);
        outcomes.put("method", methodOutcome);

        var timeoutOutcome = interpolateIfPresent(interpolation, runtimeData, outcomes, "timeout", target.getTimeout());

        var bodyOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getBody());
        bodyOutcomes.forEach((key, outcome) -> outcomes.put("body." + key, outcome));

        var headerOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getHeaders());
        headerOutcomes.forEach((key, outcome) -> outcomes.put("header." + key, outcome));

        var interpolatedAuth = authInterpolation.interpolated(target.getAuth(), interpolation, runtimeData);
        interpolatedAuth.getOutcomes().forEach((key, outcome) -> outcomes.put("auth." + key, outcome));

        return new HttpRequestDefinition(
                urlOutcome.asString(),
                methodOutcome.asString(),
                asStringOrNull(timeoutOutcome),
                resolveOrNull(target.getBody(), bodyOutcomes),
                resolveOrNull(target.getHeaders(), headerOutcomes),
                interpolatedAuth,
                outcomes);
    }

    private Map<String, String> resolveOrNull(Map<String, String> original, Map<String, InterpolationOutcome> outcomes) {
        if (original == null) {
            return null;
        }
        var result = new LinkedHashMap<String, String>();
        outcomes.forEach((key, outcome) -> result.put(key, outcome.asString()));
        return result;
    }
}
