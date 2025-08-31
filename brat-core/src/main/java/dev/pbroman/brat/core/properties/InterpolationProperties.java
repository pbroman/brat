package dev.pbroman.brat.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Configuration
@ConfigurationProperties(prefix = "brat.interpolation")
@Data
public class InterpolationProperties {

    private String variableRegex = "\\$\\{%s}";
    private String groupingVariableRegex = "\\$\\{%s\\.(?<%s>.+)?}";

}
