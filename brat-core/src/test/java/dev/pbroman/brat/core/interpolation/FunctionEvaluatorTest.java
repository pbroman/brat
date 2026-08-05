package dev.pbroman.brat.core.interpolation;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.rules.EnvInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.VarsInterpolationRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionEvaluatorTest {

    private RuntimeData runtimeData;

    private FunctionEvaluator underTest;

    /**
     * The scanner arguments are resolved through — the same one that would route a call here in
     * production. No cycle to wire: the evaluator is not in the rule list, so the dispatcher is built
     * complete before either collaborator exists.
     */
    private InterpolationScanner scanner;

    @BeforeEach
    void setUp() {
        runtimeData = new RuntimeData(Map.of("stage", "dev"), Map.of("host", "example.com"));
        runtimeData.getVars().put("name", "john");
        runtimeData.getVars().put("full", "John Smith");

        underTest = new FunctionEvaluator(new FunctionRegistry(functions()));
        scanner = new InterpolationScanner(
                new InterpolationRuleDispatcher(List.of(new VarsInterpolationRule(), new EnvInterpolationRule())),
                underTest);
    }

    private static Map<String, BratFunction> functions() {
        return Map.of(
                "upper", args -> args.getFirst().toUpperCase(),
                "join", args -> String.join("-", args),
                "count", args -> String.valueOf(args.size()),
                "id", args -> args.isEmpty() ? "none" : args.getFirst(),
                "boom",
                        args -> {
                            throw new BratException("function failed");
                        },
                "nothing", args -> null);
    }

    private InterpolationOutcome evaluate(String call) {
        return underTest.evaluate(call, scanner, runtimeData);
    }

    // --- the name ---

    @Test
    void evaluate_callsANoArgumentFunctionWrittenWithoutParentheses() {
        // when
        var result = evaluate("${__count}");

        // then
        assertThat(result.value()).isEqualTo("0");
    }

    @Test
    void evaluate_callsANoArgumentFunctionWrittenWithEmptyParentheses() {
        // when — an absent argument list and an empty one mean the same thing
        var result = evaluate("${__count()}");

        // then
        assertThat(result.value()).isEqualTo("0");
    }

    @Test
    void evaluate_matchesTheFunctionNameCaseInsensitively() {
        // when / then
        assertThat(evaluate("${__UPPER(abc)}").value()).isEqualTo("ABC");
        assertThat(evaluate("${__Upper(abc)}").value()).isEqualTo("ABC");
    }

    // --- arguments ---

    @Test
    void evaluate_passesALiteralArgument() {
        // when
        var result = evaluate("${__upper(abc)}");

        // then
        assertThat(result.value()).isEqualTo("ABC");
    }

    @Test
    void evaluate_splitsArgumentsOnTopLevelCommas() {
        // when
        var result = evaluate("${__join(a, b, c)}");

        // then
        assertThat(result.value()).isEqualTo("a-b-c");
    }

    @Test
    void evaluate_trimsWhitespaceAroundArguments() {
        // when
        var result = evaluate("${__join(  a  ,  b  )}");

        // then
        assertThat(result.value()).isEqualTo("a-b");
    }

    @Test
    void evaluate_resolvesAnEmptyArgumentToTheEmptyString() {
        // when — three arguments, the middle one empty
        var result = evaluate("${__join(a,,b)}");

        // then
        assertThat(result.value()).isEqualTo("a--b");
    }

    @Test
    void evaluate_resolvesATokenArgument() {
        // when
        var result = evaluate("${__upper(${vars.name})}");

        // then
        assertThat(result.value()).isEqualTo("JOHN");
    }

    @Test
    void evaluate_resolvesATokenArgumentAmongLiterals() {
        // when
        var result = evaluate("${__join(a, ${vars.name}, c)}");

        // then
        assertThat(result.value()).isEqualTo("a-john-c");
    }

    @Test
    void evaluate_resolvesANestedCall() {
        // when — the argument is routed straight back here by the scanner
        var result = evaluate("${__upper(${__id(abc)})}");

        // then
        assertThat(result.value()).isEqualTo("ABC");
    }

    @Test
    void evaluate_treatsACommaInsideANestedTokenAsPartOfThatArgument() {
        // when — two arguments, not three
        var result = evaluate("${__count(${__join(a, b)}, c)}");

        // then
        assertThat(result.value()).isEqualTo("2");
    }

    @Test
    void evaluate_treatsACommaInsideParenthesesAsPartOfThatArgument() {
        // when — one argument, and it keeps its own parentheses
        var result = evaluate("${__id(a(b,c))}");

        // then
        assertThat(result.value()).isEqualTo("a(b,c)");
    }

    @Test
    void evaluate_takesArgumentBoundariesFromTheTemplateNotTheResolvedValue() {
        // when — the resolved value holds a space and a comma; neither may create a second argument
        runtimeData.getVars().put("csv", "a, b");
        var result = evaluate("${__count(${vars.csv})}");

        // then
        assertThat(result.value()).isEqualTo("1");
    }

    // --- quoting ---

    @Test
    void evaluate_treatsAQuotedArgumentAsOne() {
        // when — one argument containing a comma
        var result = evaluate("${__count('a, b')}");

        // then
        assertThat(result.value()).isEqualTo("1");
    }

    @Test
    void evaluate_stripsTheQuotesFromAQuotedArgument() {
        // when
        var result = evaluate("${__id('a, b')}");

        // then
        assertThat(result.value()).isEqualTo("a, b");
    }

    @Test
    void evaluate_stillInterpolatesInsideAQuotedArgument() {
        // when — quotes protect commas, they do not suppress interpolation
        var result = evaluate("${__id('${vars.name}, x')}");

        // then
        assertThat(result.value()).isEqualTo("john, x");
    }

    @Test
    void evaluate_keepsAQuotedArgumentSeparateFromTheOneAfterIt() {
        // when — the quoted argument holds a comma, so it must not become two
        var result = evaluate("${__join('a, b', c)}");

        // then
        assertThat(result.value()).isEqualTo("a, b-c");
    }

    @Test
    void evaluate_ignoresWhitespaceBetweenAClosingQuoteAndTheNextSeparator() {
        // when — the space after the closing quote belongs to neither argument
        var result = evaluate("${__join('a' , b)}");

        // then
        assertThat(result.value()).isEqualTo("a-b");
    }

    @Test
    void evaluate_treatsAQuoteInsideAnArgumentAsAnOrdinaryCharacter() {
        // when — quoting only applies when it wraps a whole argument
        var result = evaluate("${__id(it's)}");

        // then
        assertThat(result.value()).isEqualTo("it's");
    }

    // --- secrets ---

    @Test
    void evaluate_marksTheResultSecretWhenAnArgumentWas() {
        // given — a rule standing in for the secrets rule, tagging its outcome as secret-bearing
        var withSecrets = new InterpolationScanner(
                new InterpolationRuleDispatcher(List.of(new StubSecretRule(), new VarsInterpolationRule())), underTest);

        // when
        var result = underTest.evaluate("${__upper(${secrets.token})}", withSecrets, runtimeData);

        // then — the value is the function's, but it must not be reportable
        assertThat(result.value()).isEqualTo("S3CRET");
        assertThat(result.containsSecret()).isTrue();
        assertThat(result.reportingString()).doesNotContain("S3CRET").contains("***");
    }

    @Test
    void evaluate_leavesTheResultUnmarkedWhenNoArgumentWasSecret() {
        // when
        var result = evaluate("${__upper(abc)}");

        // then
        assertThat(result.containsSecret()).isFalse();
    }

    // --- reporting ---

    @Test
    void evaluate_reportsTheCallAndItsResult() {
        // when
        var result = evaluate("${__upper(abc)}");

        // then
        assertThat(result.reportingString()).contains("${__upper(abc)}").contains("ABC");
    }

    // --- failures ---

    @Test
    void evaluate_throwsForAnUnknownFunction() {
        // when / then — a typo fails rather than passing through as literal text
        assertThatThrownBy(() -> evaluate("${__uper(abc)}"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("uper");
    }

    @Test
    void evaluate_propagatesAFailureFromTheFunction() {
        // when / then
        assertThatThrownBy(() -> evaluate("${__boom(a)}"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("function failed");
    }

    @Test
    void evaluate_throwsWhenTheFunctionReturnsNull() {
        // when / then — a missing value must not be mistaken for a resolved one
        assertThatThrownBy(() -> evaluate("${__nothing(a)}")).isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsForAnUnclosedArgumentList() {
        // when / then
        assertThatThrownBy(() -> evaluate("${__upper(abc}")).isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsWhenTheArgumentListIsNotFollowedByTheClosingBrace() {
        // when / then
        assertThatThrownBy(() -> evaluate("${__upper(abc)xyz}")).isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsForAnUnclosedQuote() {
        // when / then
        assertThatThrownBy(() -> evaluate("${__upper('abc)}")).isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsForATokenThatIsNotACall() {
        // when / then — the scanner routes by prefix, so reaching here with anything else is a
        // wiring bug: a lookup, a call missing its closing brace, and a token with no room for a name
        assertThatThrownBy(() -> evaluate("${vars.name}")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> evaluate("${__uuid")).isInstanceOf(BratException.class);
        assertThatThrownBy(() -> evaluate("${__}")).isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsForACallNamingNoFunction() {
        // when / then — an argument list with nothing in front of it
        assertThatThrownBy(() -> evaluate("${__(abc)}"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("names no function");
    }

    @Test
    void evaluate_throwsForNullCall() {
        // when / then
        assertThatThrownBy(() -> underTest.evaluate(null, scanner, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsForNullInterpolation() {
        // when / then
        assertThatThrownBy(() -> underTest.evaluate("${__upper(abc)}", null, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void evaluate_throwsForNullRuntimeData() {
        // when / then
        assertThatThrownBy(() -> underTest.evaluate("${__upper(abc)}", scanner, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_throwsForANullRegistry() {
        // when / then
        assertThatThrownBy(() -> new FunctionEvaluator(null)).isInstanceOf(BratException.class);
    }

    /**
     * Stands in for the secrets rule: resolves {@code ${secrets.…}} and tags the outcome as
     * secret-bearing, which is the only property these tests need from it.
     */
    private static final class StubSecretRule implements InterpolationRule {
        @Override
        public InterpolationOutcome outcome(String input, RuntimeData runtimeData) {
            if (!input.startsWith("${secrets.")) {
                return new InterpolationOutcome(input, input);
            }
            return new InterpolationOutcome("s3cret", input + " → ***", true);
        }
    }
}
