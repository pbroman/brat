package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.asStringOrNull;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateIfPresent;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateMapWithOutcomes;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolatedBodyFile;
import static dev.pbroman.brat.core.util.Constants.BODY_STRING;
import static dev.pbroman.brat.core.util.Constants.FILE_BODY;

/**
 * Interpolates every field of an {@link HttpRequestDefinition}.
 */
public final class HttpRequestDefinitionInterpolator implements RequestDefinitionInterpolator<HttpRequestDefinition> {

    private final ConfigDataInterpolator<Auth> authInterpolation;

    /**
     * Constructs an interpolator delegating the nested {@link Auth}.
     *
     * @param authInterpolation the interpolator this delegates the nested {@link Auth} to
     */
    public HttpRequestDefinitionInterpolator(ConfigDataInterpolator<Auth> authInterpolation) {
        this.authInterpolation = authInterpolation;
    }

    @Override
    public Class<HttpRequestDefinition> definitionType() {
        return HttpRequestDefinition.class;
    }

    /**
     * Interpolates the request's {@code url}, {@code method}, {@code timeout}, every {@code body} and
     * {@code header} value, and the nested {@code auth} block.
     * <p>
     * Outcome keys are {@code url}, {@code method}, {@code timeout}, then {@code body.<key>},
     * {@code header.<name>}, {@code args.<key>} and {@code auth.<field>} — a nested block is flattened
     * into this map with a prefix rather than nested, so a renderer sees one flat set of keys. Header names keep the
     * author's own capitalisation, since that is what a report should echo.
     * <p>
     * {@code url} and {@code method} are always interpolated and always recorded; everything else is
     * optional. A {@code null} {@code auth} is legal and yields no {@code auth.*} outcomes and a
     * {@code null} on the copy.
     * <p>
     * <strong>A {@code file} body is resolved here</strong>, after its path is interpolated: the file
     * is read, its content interpolated, and the result placed under {@code _bodyString} on the copy
     * — so a request handler receives a payload it never has to read from disk, and a body file may
     * hold {@code ${…}} tokens exactly as an inline body may. A bare path resolves relative to the
     * suite document, whose location the runtime data carries. The {@code body._bodyString} outcome
     * names the resolved path and the content's size rather than the content itself.
     *
     * @param target the request definition to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data; its suite location is what a bare {@code file} body path
     *        resolves against
     * @return a new request definition whose every declared field is interpolated and whose
     *         {@code body} carries a {@code _bodyString} — derived by the constructor for a
     *         {@code raw} or form-encoded body, and read from disk here for a {@code file} one
     * @throws dev.pbroman.brat.core.exception.BratException if {@code target} is already an
     *         interpolated copy; if the interpolated headers contain two names differing only in
     *         case; or if a {@code file} body cannot be resolved, read, or interpolated
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
        // A file body is resolved here, once its path has been interpolated above: the content lands
        // under _bodyString so that every handler reads one key and none of them reads a disk.
        var fileOutcome = bodyOutcomes.get(FILE_BODY);
        if (fileOutcome != null) {
            bodyOutcomes.put(BODY_STRING, interpolatedBodyFile(fileOutcome.asString(), interpolation, runtimeData));
        }
        putPrefixed(outcomes, "body.", bodyOutcomes);

        var headerOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getHeaders());
        putPrefixed(outcomes, "header.", headerOutcomes);

        // The handler's own arguments, interpolated like everything else so a ${secrets.…} inside one
        // resolves and is masked in the report. What the keys mean is the handler's business.
        var argOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getArgs());
        putPrefixed(outcomes, "args.", argOutcomes);

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
                resolveOrNull(target.getArgs(), argOutcomes),
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
