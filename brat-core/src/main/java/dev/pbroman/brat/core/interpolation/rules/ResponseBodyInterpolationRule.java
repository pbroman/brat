package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.BODY;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_BODY;
import static dev.pbroman.brat.core.util.Constants.RESPONSE_VARS;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * An {@link InterpolationRule} resolving {@code ${response.body}} to the previous response's body,
 * as text.
 */
public final class ResponseBodyInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for a response body.
     */
    public ResponseBodyInterpolationRule() {
        super(RESPONSE_BODY);
    }

    /**
     * {@code ${response.body}} carries no key, so the whole token is the namespace.
     *
     * @param input the token to test; never {@code null}
     * @return whether {@code input} is {@code ${response.body}}
     */
    @Override
    protected boolean claims(String input) {
        return claimsExactToken(input);
    }

    /**
     * {@inheritDoc}
     *
     * @throws dev.pbroman.brat.core.exception.BratException if {@code runtimeData} is {@code null}
     *         or holds no {@code responseVars}, or if the response has no body
     */
    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, RESPONSE_VARS);
        var body = runtimeData.getResponseVars().get(BODY);
        nonNull(body, "The response body is not present.");
        return body.toString();
    }
}
