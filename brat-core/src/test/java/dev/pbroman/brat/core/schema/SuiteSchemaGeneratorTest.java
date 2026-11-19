package dev.pbroman.brat.core.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import dev.pbroman.brat.core.api.resolver.ConditionPredicate;
import dev.pbroman.brat.core.resolver.condition.rules.AbstractConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.StringConditionResolverRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SuiteSchemaGeneratorTest {

    private static final ConditionPredicate ANY = (a, b, args) -> true;

    private static final YAMLMapper YAML = new YAMLMapper();

    /** A rule with one func of each shape the negation rule distinguishes. */
    private static final class ShapesRule extends AbstractConditionResolverRule {

        ShapesRule() {
            super(Map.of("isWithin", ANY, "contains", ANY, ">=", ANY));
        }

        @Override
        protected String category() {
            return "Shapes";
        }
    }

    /** A rule whose one func is already negative. */
    private static final class NegativeRule extends AbstractConditionResolverRule {

        NegativeRule() {
            super(Map.of("doesNotOverlap", ANY));
        }

        @Override
        protected String category() {
            return "Negative";
        }
    }

    private static List<Error> validate(ObjectNode schema, String yaml) {
        var compiled = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(schema.toString(), InputFormat.JSON);
        return compiled.validate(YAML.readTree(yaml));
    }

    private static List<Error> validate(String yaml) {
        return validate(SuiteSchemaGenerator.generate(List.of(new StringConditionResolverRule())), yaml);
    }

    private static List<String> offeredFuncs(ObjectNode schema, String type) {
        var offered = new ArrayList<String>();
        for (JsonNode branch :
                schema.path("$defs").path(type).path("properties").path("func").path("anyOf")) {
            for (JsonNode name : branch.path("enum")) {
                offered.add(name.asString());
            }
        }
        return offered;
    }

    private static final String ONE_REQUEST = """
            requests:
              - name: one
                requestDefinition:
                  url: http://example.org
            """;

    // --- the document ---

    @Test
    void generate_isADraft202012SchemaOfASuite() {
        // when
        var schema = SuiteSchemaGenerator.generate(List.of());

        // then
        assertThat(schema.path("$schema").asString()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
        assertThat(schema.path("title").isString()).isTrue();
        assertThat(schema.has("$id")).isFalse();
        assertThat(schema.path("properties").has("requests")).isTrue();
    }

    @Test
    void generate_acceptsAMinimalSuite() {
        // when
        var errors = validate(ONE_REQUEST);

        // then
        assertThat(errors).isEmpty();
    }

    // --- keys ---

    @Test
    void generate_namesOneDefinitionPerBoundTypeBySimpleClassName() {
        // when
        var defs = SuiteSchemaGenerator.generate(List.of()).path("$defs");

        // then
        assertThat(defs.propertyNames())
                .contains(
                        "Request",
                        "HttpRequestDefinition",
                        "HttpBody",
                        "Auth",
                        "Condition",
                        "Assertion",
                        "ChainedCondition",
                        "ResponseActions",
                        "FlowControl",
                        "RepeatUntil");
    }

    @Test
    void generate_rejectsAnUnknownKey() {
        // when
        var errors = validate("""
                requests:
                  - name: one
                    responsActions: {}
                """);

        // then
        assertThat(errors).extracting(e -> e.getInstanceLocation().toString()).containsOnly("/requests/0");
    }

    @Test
    void generate_rejectsAFieldTheCreatorDoesNotTake() {
        // when — outcomes is a field of every interpolatable type, never a key
        var errors = validate("""
                skipCondition:
                  a: x
                  func: isEmpty
                  outcomes: {}
                """);

        // then
        assertThat(errors).isNotEmpty();
    }

    @Test
    void generate_rejectsFileContentInABody() {
        // when
        var errors = validate("""
                requests:
                  - name: one
                    requestDefinition:
                      url: http://example.org
                      body:
                        file: payload.json
                        fileContent: "{}"
                """);

        // then
        assertThat(errors).isNotEmpty();
    }

    @Test
    void generate_requiresNoKey() {
        // when
        var errors = validate("{}");

        // then
        assertThat(errors).isEmpty();
    }

    // --- maps ---

    @Test
    void generate_mapTakesAnyKey() {
        // when
        var errors = validate("""
                requests:
                  - name: one
                    requestDefinition:
                      url: http://example.org
                      headers:
                        X-Anything-At-All: yes please
                """);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_mapValueIsDescribedByItsValueType() {
        // when — a mapping where a header's text belongs: the mis-indented body
        var errors = validate("""
                requests:
                  - name: one
                    requestDefinition:
                      url: http://example.org
                      headers:
                        body:
                          raw: "{}"
                """);

        // then
        assertThat(errors)
                .extracting(e -> e.getInstanceLocation().toString())
                .containsOnly("/requests/0/requestDefinition/headers/body");
    }

    @Test
    void generate_constantsTakeStructuredValues() {
        // when
        var errors = validate("""
                constants:
                  user:
                    name: John
                    roles: [admin, user]
                """);

        // then
        assertThat(errors).isEmpty();
    }

    // --- text ---

    @ParameterizedTest
    @ValueSource(strings = {"waitAfter: 50", "waitAfter: \"50\"", "waitAfter: \"${env.wait}\""})
    void generate_textAcceptsAStringOrANumber(String waitAfter) {
        // when
        var errors = validate(ONE_REQUEST + "    flowControl:\n      " + waitAfter + "\n");

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_textAcceptsABoolean() {
        // when
        var errors = validate("followRedirects: true\n" + ONE_REQUEST);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_textRejectsNull() {
        // when
        var errors = validate("description:\n" + ONE_REQUEST);

        // then
        assertThat(errors).extracting(e -> e.getInstanceLocation().toString()).containsOnly("/description");
    }

    @Test
    void generate_textRejectsASequence() {
        // when
        var errors = validate("timeout: [1, 2]\n");

        // then
        assertThat(errors).extracting(e -> e.getInstanceLocation().toString()).containsOnly("/timeout");
    }

    // --- untyped values ---

    @ParameterizedTest
    @ValueSource(strings = {"a: x", "a: 3", "a: [1, 2]", "a: {k: v}", "a: null"})
    void generate_untypedValueAcceptsAnything(String a) {
        // when
        var errors = validate("skipCondition:\n  func: isEmpty\n  " + a + "\n");

        // then
        assertThat(errors).isEmpty();
    }

    // --- enums ---

    @ParameterizedTest
    @ValueSource(strings = {"setup", "SETUP", "teardown", "MAIN"})
    void generate_enumAcceptsLowerCaseAndDeclaredCase(String phase) {
        // when
        var errors = validate(ONE_REQUEST + "    phase: " + phase + "\n");

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_enumRejectsAnUnknownConstant() {
        // when
        var errors = validate(ONE_REQUEST + "    phase: warmup\n");

        // then
        assertThat(errors).extracting(e -> e.getInstanceLocation().toString()).containsOnly("/requests/0/phase");
    }

    // --- requestDefinition ---

    @Test
    void generate_requestDefinitionWithoutProtocolIsHttp() {
        // when
        var errors = validate("""
                requests:
                  - name: one
                    requestDefinition:
                      url: http://example.org
                      verb: GET
                """);

        // then — an HTTP definition has no 'verb'
        assertThat(errors).isNotEmpty();
    }

    @Test
    void generate_requestDefinitionMayNameTheHttpProtocol() {
        // when
        var errors = validate("""
                requests:
                  - name: one
                    requestDefinition:
                      protocol: http
                      url: http://example.org
                """);

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_requestDefinitionOfAnotherProtocolIsOpen() {
        // when
        var errors = validate("""
                requests:
                  - name: one
                    requestDefinition:
                      protocol: ftp
                      host: files.example.org
                      path: /incoming
                """);

        // then
        assertThat(errors).isEmpty();
    }

    // --- body ---

    @ParameterizedTest
    @ValueSource(strings = {"raw: \"{}\"", "file: payload.json", "form: {grant_type: client_credentials}"})
    void generate_bodyAcceptsOneKind(String kind) {
        // when
        var errors = validate(ONE_REQUEST + "      body:\n        " + kind + "\n");

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_bodyRejectsTwoKinds() {
        // when
        var errors = validate(ONE_REQUEST + "      body:\n        raw: \"{}\"\n        file: payload.json\n");

        // then
        assertThat(errors).isNotEmpty();
    }

    @Test
    void generate_bodyRejectsNoKind() {
        // when
        var errors = validate(ONE_REQUEST + "      body: {}\n");

        // then
        assertThat(errors).isNotEmpty();
    }

    // --- func ---

    @Test
    void generate_funcOffersEachFuncAndItsNegation() {
        // when
        var schema = SuiteSchemaGenerator.generate(List.of(new ShapesRule()));

        // then
        assertThat(offeredFuncs(schema, "Condition"))
                .containsExactly(">=", "contains", "isNotWithin", "isWithin", "notContains");
    }

    @Test
    void generate_funcOffersNoNegationOfANameThatIsAlreadyNegative() {
        // when
        var schema = SuiteSchemaGenerator.generate(List.of(new NegativeRule()));

        // then
        assertThat(offeredFuncs(schema, "Condition")).containsExactly("doesNotOverlap");
    }

    @Test
    void generate_funcIsOfferedOnConditionsAssertionsAndChainedConditions() {
        // when
        var schema = SuiteSchemaGenerator.generate(List.of(new ShapesRule()));

        // then
        assertThat(offeredFuncs(schema, "Assertion")).isEqualTo(offeredFuncs(schema, "Condition"));
        assertThat(offeredFuncs(schema, "ChainedCondition")).isEqualTo(offeredFuncs(schema, "Condition"));
    }

    @Test
    void generate_funcListsANameOfferedByTwoRulesOnce() {
        // when
        var schema = SuiteSchemaGenerator.generate(List.of(new ShapesRule(), new ShapesRule()));

        // then
        assertThat(offeredFuncs(schema, "Condition")).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    void generate_funcAcceptsANameNoRuleOffers() {
        // given — a plugin's func, or another spelling of a core one
        var schema = SuiteSchemaGenerator.generate(List.of(new ShapesRule()));

        // when
        var errors = validate(schema, "skipCondition:\n  func: isWithinDistanceOf\n  a: x\n");

        // then
        assertThat(errors).isEmpty();
    }

    @Test
    void generate_funcOrderOfRulesDoesNotMatter() {
        // given
        var shapes = new ShapesRule();
        var string = new StringConditionResolverRule();

        // when
        var forwards = SuiteSchemaGenerator.render(SuiteSchemaGenerator.generate(List.of(shapes, string)));
        var backwards = SuiteSchemaGenerator.render(SuiteSchemaGenerator.generate(List.of(string, shapes)));

        // then
        assertThat(forwards).isEqualTo(backwards);
    }

    // --- determinism and rendering ---

    @Test
    void generate_isDeterministic() {
        // when
        var first = SuiteSchemaGenerator.render(SuiteSchemaGenerator.generate(List.of(new ShapesRule())));
        var second = SuiteSchemaGenerator.render(SuiteSchemaGenerator.generate(List.of(new ShapesRule())));

        // then
        assertThat(first).isEqualTo(second);
    }

    @Test
    void generate_returnsAFreshNodeEachCall() {
        // given
        var first = SuiteSchemaGenerator.generate(List.of());

        // when
        first.put("title", "changed");

        // then
        assertThat(SuiteSchemaGenerator.generate(List.of()).path("title").asString())
                .isNotEqualTo("changed");
    }

    @Test
    void render_indentsByTwoAndEndsInOneNewline() {
        // given
        var schema = YAML.createObjectNode();
        schema.putObject("a").put("b", 1);

        // when
        var text = SuiteSchemaGenerator.render(schema);

        // then
        assertThat(text).isEqualTo("{\n  \"a\" : {\n    \"b\" : 1\n  }\n}\n");
    }
}
