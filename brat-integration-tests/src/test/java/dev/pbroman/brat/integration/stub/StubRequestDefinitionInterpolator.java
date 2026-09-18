package dev.pbroman.brat.integration.stub;

import java.util.LinkedHashMap;

import dev.pbroman.brat.core.api.interpolation.Interpolation;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.configdata.InterpolatorUtils;

/**
 * Resolves the tokens in a {@link StubRequestDefinition}.
 *
 * <p>Registered through {@code ServiceLoader} beside the handler, and required: a runner whose
 * registered protocol has no interpolator refuses to build, so leaving this out is a wiring error
 * rather than a request that silently sends {@code ${vars.x}} as text.
 */
public final class StubRequestDefinitionInterpolator implements RequestDefinitionInterpolator<StubRequestDefinition> {

    @Override
    public Class<StubRequestDefinition> definitionType() {
        return StubRequestDefinition.class;
    }

    @Override
    public StubRequestDefinition interpolated(
            StubRequestDefinition target, Interpolation interpolation, RuntimeData runtimeData) {
        InterpolatorUtils.checkNotInterpolated(target);
        var outcomes = new LinkedHashMap<String, InterpolationOutcome>();
        var say = interpolation.outcome(target.getSay(), runtimeData);
        outcomes.put("say", say);
        return new StubRequestDefinition(say.asString(), outcomes);
    }
}
