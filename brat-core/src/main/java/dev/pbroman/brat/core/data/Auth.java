package dev.pbroman.brat.core.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * Authentication configuration for a request: a {@code type} naming the mechanism, plus the
 * {@code args} that mechanism reads.
 * <p>
 * Every mechanism, built-in or added by a plugin, has this one shape. The handler registered for the
 * {@code type} owns the meaning of {@code args} and rejects a key it does not know.
 * <p>
 * {@code final}: a new auth mechanism is added by writing an {@code AuthHandler} for its
 * {@code type}, never by subclassing this. A subclass could not be reached from a suite file anyway
 * — an {@code auth:} block carries no discriminator the loader could dispatch on, so it always binds
 * to this type.
 */
@Getter
public final class Auth extends ConfigData {

    private final String type;

    /** The mechanism's arguments; never {@code null}, unmodifiable, in the order they were declared. */
    private final Map<String, String> args;

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored {@code auth:} block to.
     *
     * @param type the auth type, possibly holding {@code ${...}} tokens
     * @param args the mechanism's arguments, or {@code null} for none. A {@code null} value inside it is
     *        kept, and rejected when the block is interpolated
     */
    @JsonCreator
    public Auth(String type, Map<String, String> args) {
        this(type, args, null);
    }

    /**
     * Constructor for an interpolated {@link Auth} object with its named outcomes.
     *
     * @param type the auth type
     * @param args the mechanism's arguments, or {@code null} for none
     * @param outcomes the named interpolation outcomes of an interpolated copy, or {@code null} on an
     *        as-authored instance
     */
    public Auth(String type, Map<String, String> args, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.type = type;
        // Copied by hand rather than with Map.copyOf, which rejects the null values authored YAML may hold.
        this.args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
