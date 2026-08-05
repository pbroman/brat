package dev.pbroman.brat.core.interpolation;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class InterpolationScannerTest extends AbstractInterpolationTest {

    @BeforeEach
    void setUp() {
        // one real function, so routing can be observed rather than mocked: the mock rule stands in
        // for the dispatcher, and anything reaching it that should have gone to the evaluator (or
        // the other way round) shows up as the wrong value
        var registry = new FunctionRegistry(
                Map.<String, BratFunction>of("upper", args -> args.getFirst().toUpperCase()));
        underTest = new InterpolationScanner(mockRule, new FunctionEvaluator(registry));
    }

    @Test
    void interpolate_inputWithNoVariables() {
        // given
        var input = "this is a test";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void interpolate_inputWithOneVariables() {
        // given
        var input = "this is a ${mock}";
        var expected = "this is a " + mockResult;

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void interpolate_inputWithTwoVariables() {
        // given
        var input = "this ${some.var} is a ${mock}";
        var expected = String.format("this %s is a %s", mockResult, mockResult);

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void interpolate_inputWithException() {
        // given
        when(mockRule.outcome(Mockito.anyString(), Mockito.any())).thenThrow(new BratException("mock"));
        var input = "this is a ${mock}";

        // when / then
        assertThatThrownBy(() -> underTest.interpolate(input, runtimeData)).isInstanceOf(BratException.class);
    }

    // --- a field that is nothing but one token keeps the resolved value's type ---

    @Test
    void interpolate_keepsTheResolvedTypeWhenTheFieldIsASingleToken() {
        // given — the rule resolves to a structure, not to text
        var items = List.of("a", "b");
        when(mockRule.outcome("${mock}", runtimeData)).thenReturn(new InterpolationOutcome(items, "${mock} → [a, b]"));

        // when
        var result = underTest.interpolate("${mock}", runtimeData);

        // then
        assertThat(result).isInstanceOf(List.class).isEqualTo(items);
    }

    @Test
    void interpolate_stringifiesATokenEmbeddedInSurroundingText() {
        // given — the same structured value, but now it has to be spliced into a string
        var items = List.of("a", "b");
        when(mockRule.outcome("${mock}", runtimeData)).thenReturn(new InterpolationOutcome(items, "${mock} → [a, b]"));

        // when
        var result = underTest.interpolate("items: ${mock}", runtimeData);

        // then
        assertThat(result).isEqualTo("items: [a, b]");
    }

    @Test
    void interpolate_treatsTwoAdjacentTokensAsTwoTokens() {
        // given — the token pattern is lazy, so a `matches()` check would take "${mock}${mock}"
        // for a single token and pass the first value straight through
        var input = "${mock}${mock}";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(mockResult + mockResult);
    }

    // --- routing: ${__…} goes to the evaluator, everything else to the dispatcher ---

    @Test
    void interpolate_routesACallToTheFunctionEvaluatorWhole() {
        // given — the regex this replaced cut this at the inner closing brace; the mock rule
        // resolves the argument once the evaluator sends it back through the scanner
        var input = "${__upper(${mock})}";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(mockResult.toUpperCase());
    }

    @Test
    void interpolate_routesALookupToTheDispatcher() {
        // when
        var result = underTest.interpolate("${mock}", runtimeData);

        // then — never offered to the evaluator, which knows no such function
        assertThat(result).isEqualTo(mockResult);
    }

    @Test
    void interpolate_splicesACallAndALookupInOneField() {
        // when
        var result = underTest.interpolate("${mock} then ${__upper(abc)}", runtimeData);

        // then
        assertThat(result).isEqualTo(mockResult + " then ABC");
    }

    @Test
    void interpolate_failsForAnUnknownFunctionRatherThanPassingItThrough() {
        // when / then — unlike an unrecognised namespace, a call is BRAT-specific and a typo is fatal
        assertThatThrownBy(() -> underTest.interpolate("${__nosuch(a)}", runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void interpolate_leavesAnUnbalancedTokenAlone() {
        // given — no closing brace anywhere after the "${", so there is no token at all
        var input = "cost: ${100 or so";

        // when
        var result = underTest.interpolate(input, runtimeData);

        // then
        assertThat(result).isEqualTo(input);
    }

    @Test
    void interpolate_resolvesEachOccurrenceOfARepeatedTokenSeparately() {
        // given — a rule that answers differently each time, as a generator function will
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        when(mockRule.outcome(eq("${mock}"), any())).thenAnswer(call -> {
            var next = "value" + counter.incrementAndGet();
            return new InterpolationOutcome(next, next);
        });

        // when
        var result = underTest.interpolate("${mock} and ${mock}", runtimeData);

        // then — not "value1 and value1", which replacing by text would have produced
        assertThat(result).isEqualTo("value1 and value2");
        assertThat(counter).hasValue(2);
    }

    @Test
    void interpolate_splicesEachTokenAtItsOwnPosition() {
        // given
        when(mockRule.outcome(eq("${a}"), any())).thenReturn(new InterpolationOutcome("A", "A"));
        when(mockRule.outcome(eq("${b}"), any())).thenReturn(new InterpolationOutcome("B", "B"));

        // when
        var result = underTest.interpolate("<${a}|${b}>", runtimeData);

        // then
        assertThat(result).isEqualTo("<A|B>");
    }
}
