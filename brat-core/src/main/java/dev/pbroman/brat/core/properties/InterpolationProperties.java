package dev.pbroman.brat.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;
import lombok.Setter;

@Configuration
@ConfigurationProperties(prefix = "brat.interpolation")
@Getter
@Setter
public class InterpolationProperties {

    private String variableRegex = "\\$\\{%s}";
    private String groupingVariableRegex = "\\$\\{%s\\.(?<%s>.+)?}";

}
