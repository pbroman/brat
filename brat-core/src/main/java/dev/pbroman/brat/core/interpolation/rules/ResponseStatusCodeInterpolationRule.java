package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_STATUS_CODE;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Constants.STATUS_CODE;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * An {@link InterpolationRule} resolving {@code ${response.statusCode}} to the previous response's
 * status code.
 */
public final class ResponseStatusCodeInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for status codes.
     */
    public ResponseStatusCodeInterpolationRule() {
        super(RESPONSE_STATUS_CODE);
    }

    /**
     * {@code ${response.statusCode}} carries no key, so the whole token is the namespace.
     *
     * @param input the token to test; never {@code null}
     * @return whether {@code input} is {@code ${response.statusCode}}
     */
    @Override
    protected boolean claims(String input) {
        return claimsExactToken(input);
    }

    /**
     * {@inheritDoc}
     *
     * @throws dev.pbroman.brat.core.exception.BratException if {@code runtimeData} is {@code null}
     *         or holds no {@code responseVars}, or if the response has no status code
     */
    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, RESPONSE_VARS);
        var statusCode = runtimeData.getResponseVars().get(STATUS_CODE);
        nonNull(statusCode, "The response status code is not present.");
        return statusCode.toString();
    }
}
