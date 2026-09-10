package dev.pbroman.brat.core.data;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins that every authored data type binds from YAML with its intended shape — constructor-bound,
 * no setters beyond the two deliberate ones, and with the interpolated-copy constructor unreachable.
 * <p>
 * These are the tests that would fail if {@code -parameters} were dropped from the build or a
 * {@code @JsonCreator} were removed, both of which are otherwise silent.
 */
class ConfigDataBindingTest {

    private final YAMLMapper mapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    void bind_condition() {
        // given
        var yaml = """
                func: isEqualTo
                a: "${response.statusCode}"
                b: "200"
                args:
                  offset: "0.01"
                """;

        // when
        var condition = mapper.readValue(yaml, Condition.class);

        // then
        assertThat(condition.getFunc()).isEqualTo("isEqualTo");
        assertThat(condition.getA()).isEqualTo("${response.statusCode}");
        assertThat(condition.getB()).isEqualTo("200");
        assertThat(condition.getArgs()).containsEntry("offset", "0.01");
        assertThat(condition.isInterpolated()).isFalse();
    }

    @Test
    void bind_assertionIncludingTheTwoSetterBoundProperties() {
        // given — severity and args are bound through setters, not through the creator
        var yaml = """
                func: isNotNull
                a: "${response.json.$.name}"
                message: "no name"
                severity: WARN
                args:
                  offset: "0.01"
                chain:
                  - func: startsWith
                    b: "Jo"
                    message: "wrong prefix"
                """;

        // when
        var assertion = mapper.readValue(yaml, Assertion.class);

        // then
        assertThat(assertion.getMessage()).isEqualTo("no name");
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.WARN);
        assertThat(assertion.getArgs()).containsEntry("offset", "0.01");
        assertThat(assertion.getChain()).singleElement().satisfies(link -> {
            assertThat(link.getFunc()).isEqualTo("startsWith");
            assertThat(link.getB()).isEqualTo("Jo");
            assertThat(link.getMessage()).isEqualTo("wrong prefix");
        });
    }

    @Test
    void bind_assertionDefaultsSeverityWhenAbsent() {
        // given
        var yaml = """
                func: isNotNull
                a: "x"
                """;

        // when
        var assertion = mapper.readValue(yaml, Assertion.class);

        // then
        assertThat(assertion.getSeverity()).isEqualTo(AssertionSeverity.FAIL);
    }

    @Test
    void bind_auth() {
        // given
        var yaml = """
                type: bearer
                token: "${secrets.serviceToken}"
                """;

        // when
        var auth = mapper.readValue(yaml, Auth.class);

        // then
        assertThat(auth.getType()).isEqualTo("bearer");
        assertThat(auth.getToken()).isEqualTo("${secrets.serviceToken}");
    }

    @Test
    void bind_httpRequestDefinition() {
        // given
        var yaml = """
                url: "${env.baseUrl}/orders"
                method: POST
                headers:
                  Content-Type: application/json
                body:
                  raw: '{"item": "widget"}'
                auth:
                  type: none
                """;

        // when
        var definition = mapper.readValue(yaml, HttpRequestDefinition.class);

        // then
        assertThat(definition.getUrl()).isEqualTo("${env.baseUrl}/orders");
        assertThat(definition.getMethod()).isEqualTo("POST");
        assertThat(definition.getHeaders()).containsEntry("Content-Type", "application/json");
        assertThat(definition.getAuth()).isNotNull();
    }

    @Test
    void bind_responseActions() {
        // given
        var yaml = """
                assertions:
                  - func: isEqualTo
                    a: "${response.statusCode}"
                    b: "201"
                setVars:
                  orderId: "${response.json.$.id}"
                """;

        // when
        var actions = mapper.readValue(yaml, ResponseActions.class);

        // then
        assertThat(actions.getAssertions()).hasSize(1);
        assertThat(actions.getSetVars()).containsEntry("orderId", "${response.json.$.id}");
    }

    @Test
    void bind_rejectsAnUnknownKey() {
        // given
        var yaml = """
                func: isEqualTo
                a: "x"
                fnuc: typo
                """;

        // then
        assertThatThrownBy(() -> mapper.readValue(yaml, Condition.class)).hasMessageContaining("fnuc");
    }

    @Test
    void bind_cannotReachTheInterpolatedCopyConstructor() {
        // given — 'outcomes' must not be an authorable key: a suite could otherwise hand-fabricate an
        // object claiming isInterpolated() == true, which the interpolators then refuse to interpolate
        var yaml = """
                func: isEqualTo
                a: "x"
                outcomes: {}
                """;

        // then
        assertThatThrownBy(() -> mapper.readValue(yaml, Condition.class)).hasMessageContaining("outcomes");
    }

    @Test
    void bind_assertionWithAChainAndNoB() {
        // given - a unary func over a subject several links then test: no b, no message anywhere
        var yaml = """
                func: isNotNull
                a: "${response.json.$.name}"
                chain:
                  - func: startsWith
                    b: "Jo"
                  - func: contains
                    b: "hn"
                """;

        // when
        var assertion = mapper.readValue(yaml, Assertion.class);

        // then - bound through the @JsonCreator constructor with b and message defaulted to null,
        // never through the (func, a, chain) convenience overload
        assertThat(assertion.getFunc()).isEqualTo("isNotNull");
        assertThat(assertion.getB()).isNull();
        assertThat(assertion.getMessage()).isNull();
        assertThat(assertion.getChain())
                .hasSize(2)
                .extracting(ChainedCondition::getFunc)
                .containsExactly("startsWith", "contains");
    }
}
