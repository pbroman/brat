package dev.pbroman.brat.core.interpolation;

import lombok.Getter;
import lombok.Setter;

/**
 * The configurable regexes behind interpolation: the shape of a plain {@code ${...}} token and of a
 * {@code ${namespace.key}} token whose key is captured as a named group.
 * <p>
 * Both carry defaults, so nothing has to configure this to run. Values are format strings, consumed
 * by {@link InterpolationPatterns} rather than used directly.
 */
@Getter
@Setter
public class InterpolationProperties {

    private String variableRegex = "\\$\\{%s}";
    private String groupingVariableRegex = "\\$\\{%s\\.(?<%s>.+)?}";
}
