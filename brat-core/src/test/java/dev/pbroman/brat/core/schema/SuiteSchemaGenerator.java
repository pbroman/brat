package dev.pbroman.brat.core.schema;

import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.classmate.ResolvedType;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.CustomPropertyDefinition;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerationContext;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.ChainedCondition;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.HttpBody;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.resolver.condition.rules.AbstractConditionResolverRule;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import static dev.pbroman.brat.core.util.Constants.HTTP;

/**
 * Generates the JSON Schema of a suite document from the types the loader binds, so the schema cannot
 * describe a shape the loader does not accept.
 * <p>
 * Test code: the schema it produces is committed as the resource {@link SuiteSchema} serves, and a
 * test fails when the committed file and a fresh generation differ.
 */
final class SuiteSchemaGenerator {

    private static final String TITLE = "BRAT suite";
    private static final String PROTOCOL = "protocol";
    private static final String FUNC = "func";
    private static final String IS = "is";
    private static final String NOT = "not";
    private static final String ALREADY_NEGATIVE = "doesNot";
    private static final Set<Class<?>> CONDITION_TYPES =
            Set.of(Condition.class, Assertion.class, ChainedCondition.class);
    private static final List<String> BODY_KINDS = List.of("raw", "file", "form");

    private SuiteSchemaGenerator() {}

    /**
     * Generates the schema of a suite document.
     * <p>
     * The document is a JSON Schema draft 2020-12 whose root describes a {@code TestSuite}, with a
     * {@code title} and no {@code $id}. It describes:
     * <ul>
     *   <li><strong>Keys</strong>: every object type the loader binds has one entry in {@code $defs},
     *       named by its simple class name, and its properties are exactly the parameters of the
     *       constructor the loader binds — the {@code @JsonCreator} one, or a record's canonical
     *       constructor. A field the constructor does not take ({@code outcomes},
     *       {@code fileContent}) is not a key. Every such object is closed:
     *       {@code additionalProperties: false}. No key is {@code required}.</li>
     *   <li><strong>Maps</strong> ({@code headers}, {@code setVars}, {@code args}, {@code form},
     *       {@code constants}…) take any key, each value described by the map's value type.</li>
     *   <li><strong>Text</strong>: a {@code String} accepts a string, a number or a boolean, because
     *       the loader coerces a scalar to text; this is also what lets every interpolatable field
     *       hold {@code ${...}}. It does not accept {@code null}, nor a sequence or mapping.</li>
     *   <li><strong>Untyped values</strong> ({@code Object}: a condition's {@code a} and {@code b}, a
     *       constant's value) accept anything.</li>
     *   <li><strong>Enums</strong> accept each constant in lower case and as declared
     *       ({@code setup}, {@code SETUP}), since the loader matches them ignoring case.</li>
     *   <li><strong>{@code requestDefinition}</strong>: when it has a {@code protocol} other than
     *       {@code http}, any object; otherwise an {@code HttpRequestDefinition}, whose properties
     *       additionally include {@code protocol} with the single value {@code http}.</li>
     *   <li><strong>An HTTP {@code body}</strong> holds exactly one of {@code raw}, {@code file} and
     *       {@code form}.</li>
     *   <li><strong>{@code func}</strong> on a condition, an assertion and a chained condition accepts
     *       any string, and offers the funcs of {@code rules}: each name {@link
     *       AbstractConditionResolverRule#funcs()} returns, plus its negation — {@code isX} gives
     *       {@code isNotX}, a name not starting with {@code is} gives {@code notX} ({@code contains}
     *       gives {@code notContains}), and two kinds of name have none: one not starting with a letter
     *       ({@code >=}), and one already negative, starting with {@code doesNot}
     *       ({@code doesNotHaveDuplicates}).
     *       Offered names are sorted and listed once each.</li>
     * </ul>
     * The output is deterministic: the same rules give an identical {@link #render rendering}.
     *
     * @param rules the rules whose funcs are offered for completion; must not be {@code null}. Their
     *        order does not matter
     * @return the schema; a fresh node each call
     */
    static ObjectNode generate(Collection<? extends AbstractConditionResolverRule> rules) {
        var offered = offeredFuncs(rules);
        var config = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(
                        Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT,
                        Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES,
                        Option.DEFINITIONS_FOR_ALL_OBJECTS);
        config.forFields()
                .withIgnoreCheck(
                        field -> !creatorParameters(describedType(field)).contains(field.getName()));
        config.forFields().withCustomDefinitionProvider((field, context) -> funcDefinition(field, context, offered));
        config.forTypesInGeneral().withCustomDefinitionProvider(SuiteSchemaGenerator::typeDefinition);
        config.forTypesInGeneral().withTypeAttributeOverride((node, scope, context) -> {
            if (scope.getType().getErasedType() == HttpRequestDefinition.class) {
                node.withObject("properties").putObject(PROTOCOL).put("const", HTTP);
            }
            if (scope.getType().getErasedType() == HttpBody.class) {
                var oneOf = node.putArray("oneOf");
                for (var kind : BODY_KINDS) {
                    oneOf.addObject().putArray("required").add(kind);
                }
            }
        });
        var schema = new SchemaGenerator(config.build()).generateSchema(TestSuite.class);
        schema.put("title", TITLE);
        return schema;
    }

    /** The type whose properties a field is collected for, which for an inherited field is the subclass. */
    private static Class<?> describedType(FieldScope field) {
        return field.getDeclaringTypeMembers().mainTypeAndOverrides().getFirst().getErasedType();
    }

    /** The parameter names of the constructor the loader binds: the {@code @JsonCreator} one, or a record's. */
    private static Set<String> creatorParameters(Class<?> type) {
        if (type.isRecord()) {
            return Arrays.stream(type.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toSet());
        }
        return Arrays.stream(type.getConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(JsonCreator.class))
                .findFirst()
                .map(constructor -> Arrays.stream(constructor.getParameters())
                        .map(Parameter::getName)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private static CustomDefinition typeDefinition(ResolvedType type, SchemaGenerationContext context) {
        var erased = type.getErasedType();
        var node = context.getGeneratorConfig().createObjectNode();
        if (erased == String.class) {
            node.putArray("type").add("string").add("number").add("boolean");
            return new CustomDefinition(
                    node, CustomDefinition.INLINE_DEFINITION, CustomDefinition.INCLUDING_ATTRIBUTES);
        }
        if (erased.isEnum()) {
            var values = node.putArray("enum");
            for (var constant : erased.getEnumConstants()) {
                var declared = ((Enum<?>) constant).name();
                var lower = declared.toLowerCase(Locale.ROOT);
                values.add(lower);
                if (!lower.equals(declared)) {
                    values.add(declared);
                }
            }
            return new CustomDefinition(
                    node, CustomDefinition.INLINE_DEFINITION, CustomDefinition.EXCLUDING_ATTRIBUTES);
        }
        if (erased == RequestDefinition.class) {
            var condition = node.putObject("if");
            condition.putArray("required").add(PROTOCOL);
            condition
                    .putObject("properties")
                    .putObject(PROTOCOL)
                    .putObject("not")
                    .put("const", HTTP);
            node.putObject("then").put("type", "object");
            node.set(
                    "else",
                    context.createDefinitionReference(context.getTypeContext().resolve(HttpRequestDefinition.class)));
            return new CustomDefinition(
                    node, CustomDefinition.DefinitionType.STANDARD, CustomDefinition.EXCLUDING_ATTRIBUTES);
        }
        return null;
    }

    private static CustomPropertyDefinition funcDefinition(
            FieldScope field, SchemaGenerationContext context, Set<String> offered) {
        if (!FUNC.equals(field.getName()) || !CONDITION_TYPES.contains(describedType(field))) {
            return null;
        }
        var node = context.getGeneratorConfig().createObjectNode();
        var anyOf = node.putArray("anyOf");
        var names = anyOf.addObject().putArray("enum");
        offered.forEach(names::add);
        anyOf.addObject().put("type", "string");
        return new CustomPropertyDefinition(node, CustomDefinition.AttributeInclusion.YES);
    }

    /** Every func the rules know, plus its negation, sorted. */
    private static Set<String> offeredFuncs(Collection<? extends AbstractConditionResolverRule> rules) {
        var offered = new TreeSet<String>();
        for (var rule : rules) {
            for (var func : rule.funcs()) {
                offered.add(func);
                negation(func).ifPresent(offered::add);
            }
        }
        return offered;
    }

    private static Optional<String> negation(String func) {
        if (func.isEmpty() || !Character.isLetter(func.charAt(0)) || func.startsWith(ALREADY_NEGATIVE)) {
            return Optional.empty();
        }
        if (func.startsWith(IS) && func.length() > IS.length() && Character.isUpperCase(func.charAt(IS.length()))) {
            return Optional.of(IS + "Not" + func.substring(IS.length()));
        }
        return Optional.of(NOT + Character.toUpperCase(func.charAt(0)) + func.substring(1));
    }

    /**
     * Renders a schema the way it is committed: pretty-printed with two-space indentation, ending in a
     * single newline.
     *
     * @param schema the schema to render; must not be {@code null}
     * @return the text of the committed file
     */
    static String render(ObjectNode schema) {
        var printer = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("  ", "\n"));
        return JsonMapper.builder().build().writer().with(printer).writeValueAsString(schema) + "\n";
    }
}
