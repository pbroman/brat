package dev.pbroman.brat.core.interpolation.functions;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.function.BinaryOperator;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.decimal;
import static dev.pbroman.brat.core.api.interpolation.FunctionArgs.requireArgCount;

/**
 * Arithmetic on two numbers, for the paging and quantity fiddling a suite needs.
 * <p>
 * <strong>Decimal, not floating point</strong>: {@code ${__add(0.1, 0.2)}} is {@code 0.3}, and
 * {@code ${__add(1, 2)}} is {@code 3} rather than {@code 3.0} — a result is written in its shortest
 * exact form, without an exponent, because it usually goes straight into a URL or a body.
 *
 * <dl>
 *   <dt>{@code ${__add(a, b)}}, {@code ${__subtract(a, b)}}, {@code ${__multiply(a, b)}}</dt>
 *   <dd>Exact. {@code ${__add(${vars.page}, 1)}} for the next page.</dd>
 *   <dt>{@code ${__divide(a, b)}}</dt>
 *   <dd>To 16 significant digits, which is where a non-terminating result such as {@code 10 / 3}
 *       stops. Dividing by zero is an error.</dd>
 *   <dt>{@code ${__mod(a, b)}}</dt>
 *   <dd>The remainder, taking the sign of {@code a}. A zero divisor is an error.</dd>
 * </dl>
 */
public final class MathFunctions {

    private static final MathContext DECIMAL_16 = MathContext.DECIMAL64;

    private MathFunctions() {
        // no instances
    }

    /**
     * The math functions, keyed by their bare names.
     *
     * @return the functions
     */
    public static Map<String, BratFunction> functions() {
        return Map.of(
                "add", args -> apply("add", args, BigDecimal::add),
                "subtract", args -> apply("subtract", args, BigDecimal::subtract),
                "multiply", args -> apply("multiply", args, BigDecimal::multiply),
                "divide",
                        args -> apply(
                                "divide",
                                args,
                                (a, b) -> nonZero("divide", a, b).divide(b, DECIMAL_16)),
                "mod", args -> apply("mod", args, (a, b) -> nonZero("mod", a, b).remainder(b)));
    }

    private static String apply(String name, List<String> args, BinaryOperator<BigDecimal> operation) {
        requireArgCount(name, args, 2, 2);
        var result = operation.apply(decimal(name, args.getFirst()), decimal(name, args.get(1)));
        return result.stripTrailingZeros().toPlainString();
    }

    /**
     * Returns {@code a} once {@code b} is known to be a usable divisor.
     */
    private static BigDecimal nonZero(String name, BigDecimal a, BigDecimal b) {
        if (b.signum() == 0) {
            throw new BratException("__" + name + " cannot divide " + a.toPlainString() + " by zero");
        }
        return a;
    }
}
