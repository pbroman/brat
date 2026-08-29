package dev.pbroman.brat.core.data.result;

import dev.pbroman.brat.core.data.Condition;

/**
 * A {@code setVars} capture that could not be resolved.
 * <p>
 * Deliberately not an {@link AssertionResult}: that type requires a {@link Condition}, and a capture
 * has none — synthesizing one would print a condition the author never wrote.
 * <p>
 * <strong>It carries no severity: every capture is required.</strong> There is no spelling for an
 * optional capture — {@code ${response.json.…}} takes no {@code :-} fallback, because a JSONPath may
 * legitimately contain one — so a capture that cannot be resolved is always reported.
 *
 * @param name the variable the capture would have set
 * @param expression the authored expression that failed to resolve
 * @param message why it failed
 */
public record CaptureFailure(String name, String expression, String message) {}
