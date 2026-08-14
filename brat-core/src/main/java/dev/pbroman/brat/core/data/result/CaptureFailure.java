package dev.pbroman.brat.core.data.result;

/**
 * A {@code setVars} capture that could not be resolved.
 * <p>
 * Deliberately not an {@link AssertionResult}: that type requires a {@link
 * dev.pbroman.brat.core.data.Condition}, and a capture has none — synthesizing one would print a
 * condition the author never wrote.
 * <p>
 * It carries no severity, because the {@code :-} fallback already expresses "capture if present": an
 * author who writes {@code ${response.json.$.id:-}} has said the capture is optional, and one who
 * does not has said it is required.
 *
 * @param name the variable the capture would have set
 * @param expression the authored expression that failed to resolve
 * @param message why it failed
 */
public record CaptureFailure(String name, String expression, String message) {}
