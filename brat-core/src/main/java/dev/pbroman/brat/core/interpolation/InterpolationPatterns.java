package dev.pbroman.brat.core.interpolation;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;

import java.util.regex.Pattern;

import lombok.Getter;

/**
 * Builds the regexes and {@link Pattern}s the interpolation rules match tokens with, from the
 * {@link InterpolationProperties} it is constructed with.
 * <p>
 * An instance, not a static utility: the patterns follow from configurable properties, so every rule
 * is handed the same instance rather than compiling its own.
 */
public class InterpolationPatterns {

    @Getter
    private final InterpolationProperties properties;

    /**
     * Constructs the patterns from the regexes configured for them.
     *
     * @param properties the regexes the patterns are built from
     */
    public InterpolationPatterns(InterpolationProperties properties) {
        this.properties = properties;
    }

    /**
     * The pattern matching any {@code ${...}} token.
     *
     * @return the pattern
     */
    public Pattern getVariablePattern() {
        return Pattern.compile(String.format(properties.getVariableRegex(), ".*?"));
    }

    /**
     * Wraps a bare name into token form, e.g. {@code rj.$.name} into {@code ${rj.$.name}}.
     *
     * @param variable the bare name
     * @return the name in token form
     */
    public String wrapAsVariable(String variable) {
        return String.format(properties.getVariableRegex().replace("\\", ""), variable);
    }

    /**
     * The regex matching one named token exactly, e.g. {@code ${sc}}.
     *
     * @param variable the token name
     * @return the regex
     */
    public String getRegexForVariable(String variable) {
        return String.format(properties.getVariableRegex(), variable);
    }

    /**
     * The regex matching a {@code ${namespace.key}} token, capturing the key as a named group.
     *
     * @param variable the namespace
     * @return the regex
     */
    public String getGroupingRegexForVariable(String variable) {
        return String.format(properties.getGroupingVariableRegex(), variable, VARIABLE_GROUP_NAME);
    }

    /**
     * The compiled form of {@link #getGroupingRegexForVariable(String)}.
     *
     * @param variable the namespace
     * @return the pattern
     */
    public Pattern getGroupingPatternForVariable(String variable) {
        return Pattern.compile(getGroupingRegexForVariable(variable));
    }

}
