package dev.pbroman.brat.core.tools;

import static dev.pbroman.brat.core.util.Constants.VARIABLE_GROUP_NAME;

import java.util.regex.Pattern;

import dev.pbroman.brat.core.properties.InterpolationProperties;
import lombok.Getter;

public class InterpolationTools {

    @Getter
    private final InterpolationProperties properties;

    public InterpolationTools(InterpolationProperties properties) {
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
