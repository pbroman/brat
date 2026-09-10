package dev.pbroman.brat.core.interpolation;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.interpolation.rules.ResponseJsonInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.VarsInterpolationRule;
import org.junit.jupiter.api.Test;

import static dev.pbroman.brat.core.util.Constants.JSON;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The property the nested-token decline exists for: what a token holding another token resolves to
 * does not depend on the order the rules were registered in.
 * <p>
 * Written against the real rules rather than stubs, because the defect was a real one — the greedy
 * key group in each rule's own matcher — and both answers it used to produce were wrong in different
 * ways depending on which rule came first.
 */
class NestedTokenClaimingTest {

    private static final String NESTED_VARS_TOKEN = "${vars.${vars.inner}}";

    private static final String NESTED_JSON_TOKEN = "${vars.${response.json.$.id}}";

    private final ResponseJsonInterpolationRule jsonRule = new ResponseJsonInterpolationRule();

    private final VarsInterpolationRule varsRule = new VarsInterpolationRule();

    private RuntimeData runtimeData() {
        var runtimeData = new RuntimeData(Map.of(), Map.of(), Map.of("inner", "id", "id", "resolved"));
        runtimeData.setResponseVars(Map.of(JSON, "{\"id\": \"123\"}"));
        return runtimeData;
    }

    private String interpolate(List<InterpolationRule> rules, String input) {
        return new InterpolationScanner(
                        new InterpolationRuleDispatcher(rules), new FunctionEvaluator(new FunctionRegistry(List.of())))
                .interpolate(input, runtimeData())
                .toString();
    }

    @Test
    void interpolate_leavesANestedTokenUnchangedWhicheverRuleIsAskedFirst() {
        // when
        var varsFirst = interpolate(List.of(varsRule, jsonRule), NESTED_VARS_TOKEN);
        var jsonFirst = interpolate(List.of(jsonRule, varsRule), NESTED_VARS_TOKEN);

        // then — nobody claims it, so the field passes through as written, both ways round
        assertThat(varsFirst).isEqualTo(NESTED_VARS_TOKEN);
        assertThat(jsonFirst).isEqualTo(NESTED_VARS_TOKEN);
    }

    @Test
    void interpolate_leavesANestedTokenOfTwoNamespacesUnchangedWhicheverRuleIsAskedFirst() {
        // when — the case that used to blame a JSONPath the author never wrote, $['x}'], when the
        // json rule happened to be registered first
        var varsFirst = interpolate(List.of(varsRule, jsonRule), NESTED_JSON_TOKEN);
        var jsonFirst = interpolate(List.of(jsonRule, varsRule), NESTED_JSON_TOKEN);

        // then
        assertThat(varsFirst).isEqualTo(NESTED_JSON_TOKEN);
        assertThat(jsonFirst).isEqualTo(NESTED_JSON_TOKEN);
    }

    @Test
    void interpolate_stillResolvesThePlainTokensTheNestedOnesAreMadeOf() {
        // when / then — the decline must cost the ordinary shapes nothing
        assertThat(interpolate(List.of(varsRule, jsonRule), "${vars.inner}")).isEqualTo("id");
        assertThat(interpolate(List.of(jsonRule, varsRule), "${response.json.$.id}"))
                .isEqualTo("123");
    }

    @Test
    void interpolate_stillResolvesATokenArgumentInsideAFunctionCall() {
        // given — nesting is supported here and must stay so: the call is routed to the evaluator,
        // so no rule is ever asked to claim it, and the argument comes back as a plain token
        var rules = List.<InterpolationRule>of(varsRule, jsonRule);
        var scanner = new InterpolationScanner(
                new InterpolationRuleDispatcher(rules),
                new FunctionEvaluator(new FunctionRegistry(
                        List.of(BratFunction.of("upper", args -> args.getFirst().toUpperCase())))));

        // when
        var result = scanner.interpolate("${__upper(${vars.inner})}", runtimeData());

        // then
        assertThat(result).isEqualTo("ID");
    }
}
