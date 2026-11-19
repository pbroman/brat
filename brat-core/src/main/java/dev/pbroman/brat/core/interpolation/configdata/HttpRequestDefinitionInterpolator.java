package dev.pbroman.brat.core.interpolation.configdata;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.HttpHeaderUtils;

import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.checkNotInterpolated;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolateMapWithOutcomes;
import static dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils.interpolatedBodyFile;
import static org.apache.hc.core5.http.ContentType.APPLICATION_FORM_URLENCODED;
import static org.apache.hc.core5.http.HttpHeaders.CONTENT_TYPE;

/**
 * Interpolates every field of an {@link HttpRequestDefinition}.
 */
public final class HttpRequestDefinitionInterpolator implements RequestDefinitionInterpolator<HttpRequestDefinition> {

    @Override
    public Class<HttpRequestDefinition> definitionType() {
        return HttpRequestDefinition.class;
    }

    /**
     * Interpolates the request's {@code url} and {@code method}, its body, and every {@code header} and
     * {@code args} value.
     * <p>
     * Outcome keys are {@code url}, {@code method}, then the body's, then {@code header.<name>} and
     * {@code args.<key>} — a map is flattened into this one with a prefix rather than nested, so a
     * renderer sees one flat set of keys. Header names keep the author's own capitalisation, since that
     * is what a report should echo.
     * <p>
     * {@code url} and {@code method} are always interpolated and always recorded; everything else is
     * optional.
     * <p>
     * <strong>The body</strong>, by kind:
     * <ul>
     *   <li>{@code raw}: interpolated as one value, outcome {@code body.raw};</li>
     *   <li>{@code file}: its path interpolated (outcome {@code body.file}), then the file read and its
     *       content interpolated (outcome {@code body.fileContent}, which names the resolved path and the
     *       content's size rather than the content). The copy's body carries the content, so a request
     *       handler never reads from disk. A bare path resolves relative to the suite document, whose
     *       location the runtime data carries;</li>
     *   <li>{@code form}: each entry's value interpolated, outcome {@code body.form.<key>}, in the order
     *       written. The copy's form holds the resolved values, which its payload encodes.</li>
     * </ul>
     * <strong>A form's {@code Content-Type}.</strong> When the body is a form and the headers declare no
     * {@code Content-Type} (under any capitalisation), the copy gains
     * {@code Content-Type: application/x-www-form-urlencoded}, after the authored headers, with the
     * outcome {@code header.Content-Type} reading {@code body form → application/x-www-form-urlencoded}.
     * A declared one is kept as it resolved, and the copy's constructor checks it.
     *
     * @param target the request definition to interpolate
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data; its suite location is what a bare {@code file} body path
     *        resolves against
     * @return a new request definition whose every declared field is interpolated
     * @throws BratException if {@code target} is already an interpolated copy; if the interpolated
     *         headers contain two names differing only in case; if a {@code file} body cannot be
     *         resolved, read, or interpolated; if a value resolves to JSON {@code null}, which has no
     *         text; or if a form's declared {@code Content-Type} resolves to another media type
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

        var body = target.getBody() == null
                ? null
                : interpolatedBody(target.getBody(), interpolation, runtimeData, outcomes);

        var headerOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getHeaders());
        putPrefixed(outcomes, "header.", headerOutcomes);
        var headers = resolveOrNull(target.getHeaders(), headerOutcomes);
        if (body != null && body.getForm() != null) {
            headers = withFormContentType(headers, outcomes);
        }

        // The handler's own arguments, interpolated like everything else so a ${secrets.…} inside one
        // resolves and is masked in the report. What the keys mean is the handler's business.
        var argOutcomes = interpolateMapWithOutcomes(interpolation, runtimeData, target.getArgs());
        putPrefixed(outcomes, "args.", argOutcomes);

        return new HttpRequestDefinition(
                urlOutcome.asString(),
                methodOutcome.asString(),
                body,
                headers,
                resolveOrNull(target.getArgs(), argOutcomes),
                outcomes);
    }

    /**
     * The body with its declared kind interpolated, recording its outcomes.
     *
     * @param body the authored body
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @param outcomes the definition's outcomes, mutated
     * @return the interpolated body
     */
    private static HttpBody interpolatedBody(
            HttpBody body,
            Interpolation interpolation,
            RuntimeData runtimeData,
            Map<String, InterpolationOutcome> outcomes) {
        if (body.getRaw() != null) {
            var raw = interpolation.outcome(body.getRaw(), runtimeData);
            outcomes.put("body.raw", raw);
            return HttpBody.raw(raw.asString());
        }
        if (body.getFile() != null) {
            var path = interpolation.outcome(body.getFile(), runtimeData);
            outcomes.put("body.file", path);
            var content = interpolatedBodyFile(path.asString(), interpolation, runtimeData);
            outcomes.put("body.fileContent", content);
            return new HttpBody(null, path.asString(), null, content.asString());
        }
        return interpolatedForm(body.getForm(), interpolation, runtimeData, outcomes);
    }

    /**
     * The form with every value interpolated, recording one outcome per entry.
     *
     * @param form the authored form entries
     * @param interpolation the interpolation implementation
     * @param runtimeData the runtime data
     * @param outcomes the definition's outcomes, mutated
     * @return the interpolated form body
     */
    private static HttpBody interpolatedForm(
            Map<String, String> form,
            Interpolation interpolation,
            RuntimeData runtimeData,
            Map<String, InterpolationOutcome> outcomes) {
        var resolved = new LinkedHashMap<String, String>();
        for (var entry : form.entrySet()) {
            var outcome = interpolation.outcome(entry.getValue(), runtimeData);
            outcomes.put("body.form." + entry.getKey(), outcome);
            resolved.put(entry.getKey(), outcome.asString());
        }
        return HttpBody.form(resolved);
    }

    /**
     * {@code headers} with the form {@code Content-Type} added when none is declared, recording its
     * outcome.
     *
     * @param headers the interpolated headers, or {@code null}
     * @param outcomes the definition's outcomes, mutated
     * @return the headers to send
     */
    private static Map<String, String> withFormContentType(
            Map<String, String> headers, Map<String, InterpolationOutcome> outcomes) {
        if (HttpHeaderUtils.get(headers, CONTENT_TYPE) != null) {
            return headers;
        }
        var form = APPLICATION_FORM_URLENCODED.getMimeType();
        var withContentType = headers == null ? new LinkedHashMap<String, String>() : new LinkedHashMap<>(headers);
        withContentType.put(CONTENT_TYPE, form);
        outcomes.put("header." + CONTENT_TYPE, new InterpolationOutcome(form, "body form → " + form));
        return withContentType;
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
