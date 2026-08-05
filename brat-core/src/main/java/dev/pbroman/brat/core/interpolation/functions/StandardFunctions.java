package dev.pbroman.brat.core.interpolation.functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

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
     * Every standard function, each carrying the bare name an author writes after the {@code __}
     * prefix.
     * <p>
     * A consumer offering overrides registers these <em>first</em> and contributed functions after
     * them, since a registry lets the later of two identically named functions win.
     *
     * @return the functions, ready to construct a {@code FunctionRegistry} with; never empty, and
     *         holding no two functions of the same name
     */
    public static List<BratFunction> all() {
        return gather(List.of(
                GeneratorFunctions.functions(),
                DateTimeFunctions.functions(),
                TextFunctions.functions(),
                EncodingFunctions.functions(),
                MathFunctions.functions()));
    }

    /**
     * Flattens the groups into one list in the order given, rejecting a name defined twice.
     * <p>
     * The check exists because a duplicate here would otherwise be indistinguishable from a
     * deliberate override: a registry lets the later of two identically named functions win, so one
     * core function would silently shadow another. While the groups were maps, {@code Map.of} made
     * that a construction-time failure; this restores it now that they are lists. Names are compared
     * case-insensitively, matching how a registry looks them up.
     *
     * A name is rejected whether it repeats one from an earlier group or one from the same group;
     * both would shadow, so both fail here. Names are otherwise left to
     * {@link dev.pbroman.brat.core.interpolation.FunctionRegistry}, which is their single validator
     * — a blank name passes through this method and is rejected there. A {@code null} name is the
     * exception, because it cannot be compared without dereferencing it, and an unchecked
     * {@link NullPointerException} would escape instead of a {@link BratException}.
     *
     * @param groups the groups to flatten, each a list of functions
     * @return every function of every group, in group order; never {@code null}
     * @throws BratException if a function or its {@link BratFunction#name()} is {@code null}, or if
     *         two functions share a name, ignoring case, in the same group or across groups
     */
    static List<BratFunction> gather(List<List<BratFunction>> groups) {
        var gathered = new ArrayList<BratFunction>();
        var names = new HashSet<String>();
        for (var group : groups) {
            for (var function : group) {
                nonNull(function, "A standard function must not be null");
                var name = function.name();
                nonNull(name, "A standard function must carry a name");
                if (!names.add(name.toLowerCase(Locale.ROOT))) {
                    throw new BratException("Two standard functions are named '" + name
                            + "'. One would silently shadow the other, since a repeated name is an override");
                }
                gathered.add(function);
            }
        }
        return List.copyOf(gathered);
    }
}
