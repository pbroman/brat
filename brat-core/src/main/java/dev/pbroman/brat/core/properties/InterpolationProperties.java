package dev.pbroman.brat.core.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterpolationProperties {

    private String variableRegex = "\\$\\{%s}";
    private String groupingVariableRegex = "\\$\\{%s\\.(?<%s>.+)?}";

}
