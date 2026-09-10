package dev.pbroman.brat.core.data;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * Polling configuration for a request: repeat it until a condition holds, or until the attempts run
 * out.
 * <p>
 * Every field is authored as text so it may hold {@code ${...}} tokens — {@code maxAttempts} and
 * {@code waitBetweenAttempts} are parsed as numbers only after interpolation. Exhausting the
 * attempts <em>fails</em> the request and reports {@code messageOnFail}, rather than passing quietly:
 * a polled request that never reached its condition is not a success.
 */
@Getter
public final class RepeatUntil extends ConfigData {

    private final Condition condition;
    private final String maxAttempts;
    private final String waitBetweenAttempts;
    private final String messageOnFail;

    /**
     * Constructs an interpolated copy of a repeat-until block, carrying its named outcomes.
     *
     * @param condition the condition that ends the loop when it holds
     * @param maxAttempts the attempt ceiling, in text form, or {@code null} for the default
     * @param waitBetweenAttempts the pause between attempts in milliseconds, in text form, or
     *        {@code null} for none
     * @param messageOnFail the message to report when the attempts are exhausted, or {@code null}
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on
     *        an as-authored instance
     */
    public RepeatUntil(
            Condition condition,
            String maxAttempts,
            String waitBetweenAttempts,
            String messageOnFail,
            Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.condition = condition;
        this.maxAttempts = maxAttempts;
        this.waitBetweenAttempts = waitBetweenAttempts;
        this.messageOnFail = messageOnFail;
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored {@code repeatUntil:} block to.
     *
     * @param condition the condition that ends the loop when it holds
     * @param maxAttempts the attempt ceiling, in text form
     * @param waitBetweenAttempts the pause between attempts in milliseconds, in text form
     * @param messageOnFail the message to report when the attempts are exhausted
     */
    @JsonCreator
    public RepeatUntil(Condition condition, String maxAttempts, String waitBetweenAttempts, String messageOnFail) {
        this(condition, maxAttempts, waitBetweenAttempts, messageOnFail, null);
    }
}
