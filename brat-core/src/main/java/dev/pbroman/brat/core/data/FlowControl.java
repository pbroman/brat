package dev.pbroman.brat.core.data;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * How a request paces itself: how long to wait after it, and whether to repeat it until a condition
 * holds.
 * <p>
 * The two waits it can express are deliberately different in kind. {@code waitAfter} is the
 * <em>suite's</em> pacing and counts outside the request's elapsed time; a
 * {@link RepeatUntil#getWaitBetweenAttempts() waitBetweenAttempts} is part of what the request did to
 * get its answer and counts inside it.
 */
@Getter
public final class FlowControl extends ConfigData {

    private final String waitAfter;
    private final RepeatUntil repeatUntil;

    /**
     * Constructs an interpolated copy of a flow-control block, carrying its named outcomes.
     *
     * @param waitAfter the pause after this request in milliseconds, in text form, or {@code null}
     *        for none
     * @param repeatUntil the polling configuration, or {@code null} for a request that runs once
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on
     *        an as-authored instance
     */
    public FlowControl(String waitAfter, RepeatUntil repeatUntil, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.waitAfter = waitAfter;
        this.repeatUntil = repeatUntil;
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored {@code flowControl:} block to.
     *
     * @param waitAfter the pause after this request in milliseconds, in text form
     * @param repeatUntil the polling configuration
     */
    @JsonCreator
    public FlowControl(String waitAfter, RepeatUntil repeatUntil) {
        this(waitAfter, repeatUntil, null);
    }
}
