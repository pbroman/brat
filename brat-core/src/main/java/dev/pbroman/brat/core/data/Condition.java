package dev.pbroman.brat.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import lombok.Getter;

/**
 * A condition is a function 'a func b' that can be evaluated to true or false.
 * <p>
 * {@code sealed} rather than {@code final}: {@link Assertion} is the one designed subclass, and no
 * extender has a path to a second one — an {@code assertions:} entry binds to {@link Assertion} and a
 * {@code skipCondition:} to this type, each by field with a known static type, so nothing picks a
 * subclass at runtime.
 */
@Getter
public sealed class Condition extends ConfigData permits Assertion {

    private static final String MASK = "***";

    private final String func;
    private final Object a;
    private final Object b;

    /**
     * The func's own arguments, e.g. {@code offset} for {@code isCloseTo}. Never {@code null};
     * empty when the author declared none. A func needing an argument takes it from here rather
     * than from a field of its own, so adding one never changes this type.
     * <p>
     * Named {@code args} rather than {@code params} deliberately: {@code params} is the
     * interpolation namespace behind {@code ${params.x}}, and the two would otherwise be
     * indistinguishable in a suite file and in reported outcome keys.
     */
    private final Map<String, String> args;

    /**
     * Constructor for an interpolated {@link Condition} object with its named outcomes.
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     * @param args the func's arguments, or {@code null} for none
     * @param outcomes the named interpolation outcomes
     */
    public Condition(
            String func, Object a, Object b, Map<String, String> args, Map<String, InterpolationOutcome> outcomes) {
        super(outcomes);
        this.func = func;
        this.a = a;
        this.b = b;
        this.args = args == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }

    /**
     * {@code outcomes} defaults to {@code null} (not yet an interpolated copy). This is the
     * constructor the loader binds an authored condition to, and the only one that takes
     * {@code args} — every shorter overload defaults it to empty.
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     * @param args the func's arguments, or {@code null} for none
     */
    @JsonCreator
    public Condition(String func, Object a, Object b, Map<String, String> args) {
        this(func, a, b, args, null);
    }

    /**
     * {@code args} and {@code outcomes} default to empty and {@code null}.
     *
     * @param func the function name
     * @param a the first operand
     * @param b the second operand
     */
    public Condition(String func, Object a, Object b) {
        this(func, a, b, null, null);
    }

    /**
     * {@code b} defaults to {@code null} — for unary funcs (e.g. {@code isNull}) that don't need a
     * second operand — as do {@code args} and {@code outcomes}.
     *
     * @param func the function name
     * @param a the first operand
     */
    public Condition(String func, Object a) {
        this(func, a, null, null, null);
    }

    /**
     * Renders the condition as {@code a func b}, or {@code a func} when {@code b} is {@code null} —
     * the form it takes in skip reasons and failure messages. {@code args} are not shown.
     * <p>
     * <strong>An interpolated copy is masked</strong>, so the text is safe to print wherever it
     * ends up. Each operand keeps its shape — a mapping as {@code {k=v, …}}, a sequence as
     * {@code [x, …]} — and is shown part by part, from the operand down. A part with an outcome
     * recorded at its path ({@code b}, {@code b.name}, {@code b[0]}) is shown as {@code ***} when that
     * outcome {@linkplain InterpolationOutcome#containsSecret() contains a secret} and as its value
     * otherwise, whatever its shape — so a structure one token resolved to is masked or shown whole.
     * A mapping or sequence with no outcome of its own is walked. A non-{@code null} scalar with no
     * recorded outcome is shown as {@code ***}, since nothing says it is safe; {@code null} is shown
     * as {@code null}.
     * <p>
     * Only what interpolation knows to be secret is masked. A secret that reached an operand some
     * other way — echoed back in a response and read through {@code ${response.…}} — is an ordinary
     * value to interpolation and is shown as one.
     * <p>
     * An as-authored condition is shown as written: its operands still hold the {@code ${…}}
     * expressions, not what they resolve to.
     *
     * @return the rendered condition; never {@code null}
     */
    @Override
    public String toString() {
        var shownA = isInterpolated() ? masked("a", a) : a;
        if (b == null) {
            return shownA + " " + func;
        }
        var shownB = isInterpolated() ? masked("b", b) : b;
        return shownA + " " + func + " " + shownB;
    }

    private Object masked(String path, Object value) {
        var outcome = getOutcomes().get(path);
        if (outcome != null) {
            return outcome.containsSecret() ? MASK : value;
        }
        return switch (value) {
            case null -> null;
            case Map<?, ?> map -> {
                var shown = new LinkedHashMap<Object, Object>();
                for (var entry : map.entrySet()) {
                    shown.put(entry.getKey(), masked(path + "." + entry.getKey(), entry.getValue()));
                }
                yield shown;
            }
            case List<?> list -> {
                var shown = new ArrayList<>();
                for (var i = 0; i < list.size(); i++) {
                    shown.add(masked(path + "[" + i + "]", list.get(i)));
                }
                yield shown;
            }
            default -> MASK;
        };
    }
}
