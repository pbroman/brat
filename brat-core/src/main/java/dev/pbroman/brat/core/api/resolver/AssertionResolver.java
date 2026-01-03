package dev.pbroman.brat.core.api.resolver;

import java.util.List;

import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.result.AssertionResult;
import dev.pbroman.brat.core.data.runtime.RuntimeData;

public interface AssertionResolver {

    /**
     * Resolves an {@link Assertion}, testing if all contained conditions are true.
     *
     * @param assertion the {@link Assertion}
     * @return a list of assertion results
     */
    List<AssertionResult> resolve(Assertion assertion, RuntimeData runtimeData);

}
