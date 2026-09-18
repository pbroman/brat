package dev.pbroman.brat.core.interpolation.rules;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.HEADERS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_HEADERS;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;

/**
 * An {@link InterpolationRule} resolving {@code ${response.headers.<name>}} against the previous
 * response's headers.
 * <p>
 * <strong>A header's <em>first</em> value is what substitutes.</strong> The namespace holds every
 * value a repeated header carried — {@code Set-Cookie} being the everyday case — because a token
 * substitutes one string and dropping the rest at the source would lose them for good. So the
 * narrowing happens here, at the point where a string is actually needed, and the later values stay
 * in the namespace for whatever reaches them next.
 */
@Slf4j
public final class ResponseHeaderInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for response headers.
     */
    public ResponseHeaderInterpolationRule() {
        super(RESPONSE_HEADERS);
    }

    /**
     * {@inheritDoc}
     *
     * @throws dev.pbroman.brat.core.exception.BratException if {@code runtimeData} is {@code null}
     *         or holds no {@code responseVars}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, RESPONSE_VARS);
        if (runtimeData.getResponseVars().get(HEADERS) instanceof Map headers) {
            return simpleInterpolation(input, runtimeData, firstValues(headers));
        }
        return input;
    }

    /**
     * Narrows each header to the one value a token can substitute.
     * <p>
     * A value that is a collection yields its first element, and a header carrying none is left out
     * entirely, so it reaches the missing-header path rather than substituting emptiness. Any other
     * value passes through as it stands: the shared {@code simpleInterpolation} renders whatever it
     * finds, and this method exists only to stop a list rendering as {@code [a, b]}.
     * <p>
     * The result is case-insensitive like its source, which it has to be — the lookup uses the
     * author's placeholder text verbatim, and a server may send {@code content-type} for
     * {@code Content-Type}.
     *
     * @param headers the namespace's header entry, keyed by header name
     * @return a case-insensitive map of one value per header
     */
    private static Map<String, Object> firstValues(Map<?, ?> headers) {
        var narrowed = new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
        for (var header : headers.entrySet()) {
            var value = header.getValue();
            if (value instanceof Collection<?> values) {
                if (!values.isEmpty()) {
                    narrowed.put(
                            String.valueOf(header.getKey()), values.iterator().next());
                }
            } else {
                narrowed.put(String.valueOf(header.getKey()), value);
            }
        }
        return narrowed;
    }

    /**
     * {@inheritDoc}
     *
     * Additionally logs expected headers not present in the response.
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input, RuntimeData runtimeData) {
        log.warn("The header {} is not in the response", placeholder);
        return super.onMissingReplacement(placeholder, input, runtimeData);
    }
}
