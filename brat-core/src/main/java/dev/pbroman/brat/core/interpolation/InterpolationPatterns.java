package dev.pbroman.brat.core.interpolation;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;

import java.util.regex.Pattern;

import lombok.Getter;

public class InterpolationPatterns {

    @Getter
    private final InterpolationProperties properties;

    public InterpolationPatterns(InterpolationProperties properties) {
        this.properties = properties;
    }

    public Pattern getVariablePattern() {
        return Pattern.compile(String.format(properties.getVariableRegex(), ".*?"));
    }

    public String wrapAsVariable(String variable) {
        return String.format(properties.getVariableRegex().replace("\\", ""), variable);
    }

    public String getRegexForVariable(String variable) {
        return String.format(properties.getVariableRegex(), variable);
    }

    public String getGroupingRegexForVariable(String variable) {
        return String.format(properties.getGroupingVariableRegex(), variable, VARIABLE_GROUP_NAME);
    }

    public Pattern getGroupingPatternForVariable(String variable) {
        return Pattern.compile(getGroupingRegexForVariable(variable));
    }

}
