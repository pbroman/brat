package dev.pbroman.brat.core.interpolation;

import java.util.Comparator;
import java.util.List;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;

public class InterpolationRuleDispatcher implements Interpolation {

    protected final List<InterpolationRule> rules;

    public InterpolationRuleDispatcher(List<InterpolationRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(InterpolationRule::priority).reversed()).toList();
    }

    @Override
    public String interpolate(String input, RuntimeData runtimeData) throws ValidationException {
        for (var rule : rules) {
            input = rule.interpolate(input, runtimeData);
        }
        return input;
    }
}
