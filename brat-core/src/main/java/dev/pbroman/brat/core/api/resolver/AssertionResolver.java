package dev.pbroman.brat.core.api.resolver;

import java.util.List;

import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.result.AssertionFail;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.ValidationException;

public interface AssertionResolver {

    /**
     * Resolves an {@link Assertion}, testing if all contained conditions are true.
     *
     * @param assertion the {@link Assertion}
     * @return true or false
     * @throws ValidationException if any of the conditions cannot be resolved
     */
    List<AssertionFail> resolve(Assertion assertion, RuntimeData runtimeData) throws ValidationException;

}
