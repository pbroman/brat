package dev.pbroman.brat.core.interpolation;

import static java.util.Objects.requireNonNull;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.exception.ValidationException;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.tools.InterpolationTools;

public class InterpolationHandler implements Interpolation {

    private final Interpolation dispatcher;

    private final InterpolationTools tools;

    public InterpolationHandler(Interpolation dispatcher, InterpolationTools tools) {
        this.dispatcher = dispatcher;
        this.tools = tools;
    }

    /**
     * Receives a string that may contain any number of variables. Sends all variables to the
     * {@link InterpolationRuleDispatcher} and replaces the variable patterns with the results.
     */
    @Override
    public String interpolate(String input, RuntimeData runtimeData) {
        requireNonNull(runtimeData, "runtimeData must not be null");
        var matcher = tools.getVariablePattern().matcher(input);
        while (matcher.find()) {
            try {
                var interpolation = dispatcher.interpolate(matcher.group(0), runtimeData);
                input = input.replace(matcher.group(0), interpolation);
            } catch (ValidationException e) {
                // TODO: add validation to runtime data
            }
        }
        return input;
    }
}
