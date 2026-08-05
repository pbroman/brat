package dev.pbroman.brat.core.interpolation.functions;

import java.util.HashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;

/**
 * Every function {@code brat-core} ships with, ready to hand to a {@code FunctionRegistry}.
 * <p>
 * The functions themselves are grouped by what they do — {@link GeneratorFunctions},
 * {@link DateTimeFunctions}, {@link TextFunctions}, {@link EncodingFunctions},
 * {@link MathFunctions} — and each of those documents its own. This class only gathers them, and is
 * what a consumer normally registers; a consumer wanting a narrower set can take the groups
 * individually instead.
 * <p>
 * Two properties hold across all of them: <strong>a wrong argument count is a
 * {@code BratException}</strong>, never a silently ignored extra, and <strong>no function returns
 * {@code null}</strong>.
 */
public final class StandardFunctions {

    private StandardFunctions() {
        // no instances
    }

    /**
     * Every standard function, keyed by the bare name an author writes after the {@code __} prefix.
     *
     * @return the functions, ready to construct a {@code FunctionRegistry} with; never empty
     */
    public static Map<String, BratFunction> all() {
        var all = new HashMap<String, BratFunction>();
        all.putAll(GeneratorFunctions.functions());
        all.putAll(DateTimeFunctions.functions());
        all.putAll(TextFunctions.functions());
        all.putAll(EncodingFunctions.functions());
        all.putAll(MathFunctions.functions());
        return Map.copyOf(all);
    }
}
