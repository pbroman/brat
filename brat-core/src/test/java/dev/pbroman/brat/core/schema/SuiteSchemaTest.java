package dev.pbroman.brat.core.schema;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.RepeatUntil;
import dev.pbroman.brat.core.data.Request;
import dev.pbroman.brat.core.data.ResponseActions;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.resolver.condition.rules.AbstractConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.BooleanConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.DateConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.FormatConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.JsonConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.NumberConditionResolverRule;
import dev.pbroman.brat.core.resolver.condition.rules.StringConditionResolverRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The committed schema: that it is what the generator produces, and that its keys are the ones the
 * binder actually binds — asked of Jackson, not of the generator's own reading of the types.
 * <p>
 * Run with {@code -Dbrat.schema.write=true} to rewrite the committed file from a fresh generation
 * instead of comparing.
 */
class SuiteSchemaTest {

    private static final Path COMMITTED = Path.of("src/main/resources/" + SuiteSchema.RESOURCE);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    // Mirrors the rule list in Brat's builder, minus the null rule, which lists no funcs: a rule added
    // there belongs here too, or its funcs are accepted but not offered.
    /** Every condition rule core ships that extends the base rule, so every core func is offered. */
    static List<AbstractConditionResolverRule> coreRules() {
        return List.of(
                new StringConditionResolverRule(),
                new NumberConditionResolverRule(),
                new BooleanConditionResolverRule(),
                new DateConditionResolverRule(),
                new FormatConditionResolverRule(),
                new JsonConditionResolverRule());
    }

    private static String committed() {
        return SuiteSchema.json();
    }

    private static Set<String> boundKeys(Class<?> type) {
        var context = JSON._deserializationContext();
        var keys = new HashSet<String>();
        for (var property :
                context.introspectBeanDescription(JSON.constructType(type)).findProperties()) {
            if (property.couldDeserialize()) {
                keys.add(property.getName());
            }
        }
        return keys;
    }

    private static Set<String> schemaKeys(JsonNode definition) {
        return new HashSet<>(definition.path("properties").propertyNames());
    }

    @Test
    void committedSchema_isWhatTheGeneratorProduces() throws IOException {
        // given
        var generated = SuiteSchemaGenerator.render(SuiteSchemaGenerator.generate(coreRules()));
        if (Boolean.getBoolean("brat.schema.write")) {
            Files.writeString(COMMITTED, generated, StandardCharsets.UTF_8);
        }

        // when
        var committed = committed();

        // then
        assertThat(committed)
                .as("the committed schema is stale; rerun this test with -Dbrat.schema.write=true and review the diff")
                .isEqualTo(generated);
    }

    @Test
    void committedSchema_rootKeysAreTheKeysJacksonBindsOnATestSuite() throws IOException {
        // when
        var schema = JSON.readTree(committed());

        // then
        assertThat(schemaKeys(schema)).isEqualTo(boundKeys(TestSuite.class));
    }

    @ParameterizedTest
    @ValueSource(
            classes = {
                Request.class,
                HttpBody.class,
                Auth.class,
                Condition.class,
                Assertion.class,
                ChainedCondition.class,
                ResponseActions.class,
                FlowControl.class,
                RepeatUntil.class
            })
    void committedSchema_definitionKeysAreTheKeysJacksonBinds(Class<?> type) throws IOException {
        // when
        var definition = JSON.readTree(committed()).path("$defs").path(type.getSimpleName());

        // then
        assertThat(schemaKeys(definition)).isEqualTo(boundKeys(type));
    }

    @Test
    void committedSchema_httpDefinitionKeysAreTheKeysJacksonBindsPlusProtocol() throws IOException {
        // given — the loader consumes protocol before binding, so Jackson never sees it
        var expected = boundKeys(HttpRequestDefinition.class);
        expected.add("protocol");

        // when
        var definition = JSON.readTree(committed()).path("$defs").path(HttpRequestDefinition.class.getSimpleName());

        // then
        assertThat(schemaKeys(definition)).isEqualTo(expected);
    }
}
