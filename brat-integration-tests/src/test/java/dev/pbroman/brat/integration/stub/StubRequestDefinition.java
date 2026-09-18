package dev.pbroman.brat.integration.stub;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.data.ConfigData;

/**
 * What a {@code protocol: stub} request says, and nothing HTTP has.
 *
 * <p>A <em>sibling</em> of {@code HttpRequestDefinition} rather than a subclass, which is the shape a
 * protocol plugin writes: extend {@link ConfigData} so it can be interpolated, implement
 * {@link RequestDefinition} so a handler can execute it.
 */
public final class StubRequestDefinition extends ConfigData implements RequestDefinition {

    private final String say;

    /**
     * Constructs the definition a suite document binds to.
     *
     * @param say what the handler should echo back, possibly holding {@code ${...}} tokens
     */
    @JsonCreator
    public StubRequestDefinition(String say) {
        this(say, null);
    }

    /**
     * Constructs an interpolated copy.
     *
     * @param say the resolved text
     * @param outcomes what was substituted
     */
    public StubRequestDefinition(String say, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.say = say;
    }

    /** @return what the handler should echo back */
    public String getSay() {
        return say;
    }

    @Override
    public String protocol() {
        return "stub";
    }
}
