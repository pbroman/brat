package dev.pbroman.brat.core.api.interpolation;

import java.util.List;
import java.util.function.Function;

import dev.pbroman.brat.core.exception.BratException;

/**
 * One function callable from a suite as {@code ${__name(arg, …)}}.
 * <p>
 * A function <strong>carries its own name</strong>. It is selected by that name rather than by being
 * offered every token, so the name has to travel inside the object: a name held beside it — in a map
 * key, in a bean definition — does not survive registration paths that yield bare instances, and
 * {@code ServiceLoader} is exactly such a path.
 * <p>
 * Arguments arrive fully resolved: a nested token has already been interpolated, so an implementation
 * never sees a {@code ${…}} and must never try to resolve one itself — doing so would re-interpret
 * data as a template, which is the injection hole the single-pass design exists to avoid.
 * <p>
 * Arguments and results are text. A function that conceptually produces a number, a date or a
 * structure formats it: the caller is putting the result into a request body, a header or a URL, or
 * comparing it with a condition, and all of those are text. Structured values come from the response
 * ({@code ${response.json.$.items}}), which already yields them.
 */
public interface BratFunction {

    /**
     * The name an author writes after the {@code __} prefix, so a function named {@code uuid} is
     * called as <code>${__uuid}</code>.
     * <p>
     * Must not be {@code null} or blank, must not itself carry the {@code __} prefix — that is
     * syntax, not part of the name — and must be constant for the life of the function. Those
     * requirements are stated rather than enforced here: {@link dev.pbroman.brat.core.interpolation.FunctionRegistry}
     * is the single place that validates them, so that an implementation not built through
     * {@link #of} is checked identically.
     * <p>
     * A registry matches names case-insensitively, so {@code uuid} answers <code>${__UUID}</code>
     * too.
     *
     * @return the bare function name
     */
    String name();

    /**
     * Applies the function to its arguments.
     *
     * @param args the resolved arguments, in the order written; never {@code null}, and empty for a
     *        no-argument call. Individual arguments may be empty strings but are never {@code null}
     * @return the result; {@code null} is not permitted — a function that has no answer throws
     *         instead, so that a missing value cannot be mistaken for a resolved one
     * @throws BratException if the arguments are the wrong number or kind for this function, or if
     *         producing the value fails
     */
    String apply(List<String> args);

    /**
     * Pairs a name with an implementation, so a function can still be written as a lambda or a
     * method reference.
     * <p>
     * <strong>Does not validate the name.</strong> A blank name, or one carrying the {@code __}
     * prefix, produces a function that is rejected later, when a registry is built from it. That is
     * deliberate: {@link dev.pbroman.brat.core.interpolation.FunctionRegistry} has to check every
     * implementation it is given, including ones written as classes rather than through this method,
     * and a second name validator here would be a copy of those rules free to drift from them.
     * <p>
     * The implementation is the exception, because this method is the only thing that can see it. A
     * {@code null} {@code impl} is invisible to a registry — nothing detects it without calling the
     * function — so it would otherwise surface as a {@link NullPointerException} from inside the
     * returned object, on some later call, rather than as a {@link BratException} at the point the
     * mistake was made.
     * <p>
     * The returned function's {@code toString} reports the name together with the class
     * {@code impl} was written in. Every function built here shares one anonymous class, so the
     * class alone identifies nothing; the implementation's origin is what tells a core function
     * apart from a plugin's in a log line, which is what makes an override traceable.
     *
     * @param name the value the returned function's {@link #name()} reports, unexamined
     * @param impl the implementation the returned function's {@link #apply} delegates to
     * @return a function reporting {@code name} and delegating to {@code impl}
     * @throws BratException if {@code impl} is {@code null}
     */
    static BratFunction of(String name, Function<List<String>, String> impl) {
        if (impl == null) {
            throw new BratException("The implementation of the function '" + name + "' must not be null");
        }
        return new BratFunction() {

            @Override
            public String name() {
                return name;
            }

            @Override
            public String apply(List<String> args) {
                return impl.apply(args);
            }

            @Override
            public String toString() {
                return "BratFunction.of(" + name + ", " + originOf(impl) + ")";
            }
        };
    }

    /**
     * The class an implementation was written in, with the synthetic suffix a lambda or method
     * reference carries stripped off, so {@code TextFunctions::upper} reports {@code TextFunctions}
     * rather than {@code TextFunctions$$Lambda/0x00007f…} — the hex part changes per run and would
     * make one override look like several.
     *
     * @param impl the implementation to describe
     * @return the declaring class name
     */
    private static String originOf(Function<List<String>, String> impl) {
        var className = impl.getClass().getName();
        var syntheticSuffix = className.indexOf("$$Lambda");
        return syntheticSuffix < 0 ? className : className.substring(0, syntheticSuffix);
    }
}
