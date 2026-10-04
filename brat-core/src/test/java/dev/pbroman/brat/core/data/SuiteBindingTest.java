package dev.pbroman.brat.core.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins that the suite tree binds from YAML with the shape its types declare.
 * <p>
 * The {@code RequestDefinition} → {@code HttpRequestDefinition} mapping below stands in for the
 * loader's protocol lookup: {@code Request.requestDefinition} is typed to the interface, so something
 * has to say which implementation an authored block becomes. {@code SuiteLoaderTest} covers the real
 * lookup.
 */
class SuiteBindingTest {

    private final YAMLMapper mapper = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .addModule(new SimpleModule().addAbstractTypeMapping(RequestDefinition.class, HttpRequestDefinition.class))
            .build();

    @Test
    void bind_theWholeFileFromTheSuiteAuthorDocs() {
        // given — the whole authored vocabulary in one document
        var yaml = """
                name: order api
                description: everything the order service promises
                constants:
                  contentType: application/json
                setVars:
                  runId: "${__uuid}"
                auth:
                  type: bearer
                  token: "${secrets.serviceToken}"
                timeout: "10000"
                requests:
                  - name: create an order
                    id: order-create
                    requestDefinition:
                      url: "${env.baseUrl}/orders"
                      method: POST
                      headers:
                        Content-Type: "${constants.contentType}"
                      body:
                        raw: '{"item": "widget"}'
                    responseActions:
                      assertions:
                        - a: "${response.statusCode}"
                          func: isEqualTo
                          b: "201"
                      setVars:
                        orderId: "${response.json.$.id}"
                    flowControl:
                      waitAfter: "500"
                subSuites:
                  - name: log in
                """;

        // when
        var suite = mapper.readValue(yaml, TestSuite.class);

        // then
        assertThat(suite.name()).isEqualTo("order api");
        assertThat(suite.constants()).containsEntry("contentType", "application/json");
        assertThat(suite.setVars()).containsEntry("runId", "${__uuid}");
        assertThat(suite.auth().getType()).isEqualTo("bearer");
        assertThat(suite.timeout()).isEqualTo("10000");
        assertThat(suite.requests()).singleElement().satisfies(request -> {
            assertThat(request.id()).isEqualTo("order-create");
            assertThat(request.requestDefinition()).isInstanceOf(HttpRequestDefinition.class);
            assertThat(request.responseActions().getAssertions()).hasSize(1);
            assertThat(request.flowControl().getWaitAfter()).isEqualTo("500");
        });
        assertThat(suite.subSuites())
                .singleElement()
                .satisfies(sub -> assertThat(sub.name()).isEqualTo("log in"));
    }

    @Test
    void bind_repeatUntil() {
        // given — numbers in YAML, text on the type, because either may hold a ${...} token
        var yaml = """
                waitAfter: 500
                repeatUntil:
                  condition:
                    a: "${response.json.$.status}"
                    func: isEqualTo
                    b: COMPLETE
                  maxAttempts: 10
                  waitBetweenAttempts: 2000
                  messageOnFail: the job never completed
                """;

        // when
        var flowControl = mapper.readValue(yaml, FlowControl.class);

        // then
        assertThat(flowControl.getWaitAfter()).isEqualTo("500");
        assertThat(flowControl.getRepeatUntil().getMaxAttempts()).isEqualTo("10");
        assertThat(flowControl.getRepeatUntil().getWaitBetweenAttempts()).isEqualTo("2000");
        assertThat(flowControl.getRepeatUntil().getCondition().getFunc()).isEqualTo("isEqualTo");
    }

    @Test
    void bind_defaultsEveryCollectionToEmpty() {
        // when
        var suite = mapper.readValue("name: smoke\n", TestSuite.class);

        // then — the walk iterates without guarding
        assertThat(suite.constants()).isEmpty();
        assertThat(suite.setVars()).isEmpty();
        assertThat(suite.requestHandlers()).isEmpty();
        assertThat(suite.requests()).isEmpty();
        assertThat(suite.subSuites()).isEmpty();
    }

    @Test
    void bind_acceptsARequestPhaseInAnyCase() {
        // given — the docs write `phase: setup`, the severity docs write `severity: WARN`
        var lower = mapper.readValue("name: s\nrequests:\n  - name: r\n    phase: teardown\n", TestSuite.class);
        var upper = mapper.readValue("name: s\nrequests:\n  - name: r\n    phase: TEARDOWN\n", TestSuite.class);

        // then
        assertThat(lower.requests().getFirst().phase()).isEqualTo(Phase.TEARDOWN);
        assertThat(upper.requests().getFirst().phase()).isEqualTo(Phase.TEARDOWN);
    }

    @Test
    void bind_rejectsAPhaseOnASuite() {
        // given - only requests take a phase; a suite runs where it is declared
        var yaml = """
                name: s
                subSuites:
                  - name: log in
                    phase: setup
                """;

        // then
        assertThatThrownBy(() -> mapper.readValue(yaml, TestSuite.class)).hasMessageContaining("phase");
    }

    @Test
    void bind_bindsASuitesSkipConditionAndHandlerNames() {
        // given
        var yaml = """
                name: order api
                skipCondition:
                  a: "${params.tenant}"
                  func: isNull
                requestHandlers:
                  http: mtls
                """;

        // when
        var suite = mapper.readValue(yaml, TestSuite.class);

        // then
        assertThat(suite.skipCondition().getFunc()).isEqualTo("isNull");
        assertThat(suite.requestHandlers()).containsEntry("http", "mtls");
    }

    @Test
    void bind_rejectsAnUnknownKey() {
        // given
        var yaml = "name: order api\nrequets: []\n";

        // then
        assertThatThrownBy(() -> mapper.readValue(yaml, TestSuite.class)).hasMessageContaining("requets");
    }

    @Test
    void bind_nestsSubSuitesToAnyDepth() {
        // given
        var yaml = """
                name: a
                subSuites:
                  - name: b
                    subSuites:
                      - name: c
                """;

        // when
        var suite = mapper.readValue(yaml, TestSuite.class);

        // then
        assertThat(suite.subSuites().getFirst().subSuites().getFirst().name()).isEqualTo("c");
    }

    @Test
    void constructor_copiesTheCollectionsItWasGiven() {
        // given
        var requests = new java.util.ArrayList<Request>();
        var suite = new TestSuite("s", null, null, null, null, null, null, null, requests, null);

        // when
        requests.add(new Request("r", null, null, null, null, null, null, null, null, null));

        // then
        assertThat(suite.requests()).isEmpty();
    }

    @Test
    void constructor_handsOutUnmodifiableCollections() {
        // given
        var suite = new TestSuite("s", null, null, null, null, null, null, null, List.of(), null);

        // then
        assertThatThrownBy(() -> suite.constants().put("k", "v")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void bind_aRequestDeclaringItsOwnPhaseAndHandlers() {
        // given - a setup *request*, which is what marks the login inside a suite
        var yaml = """
                name: order api
                requests:
                  - name: log in
                    phase: setup
                    requestHandlers:
                      http: mtls
                """;

        // when
        var suite = mapper.readValue(yaml, TestSuite.class);

        // then
        assertThat(suite.requests()).singleElement().satisfies(request -> {
            assertThat(request.phase()).isEqualTo(Phase.SETUP);
            assertThat(request.requestHandlers()).containsEntry("http", "mtls");
        });
    }

    @Test
    void bind_aRequestDeclaringItsOwnTimeout() {
        // given - timeout is orchestration metadata, so it sits on the request beside phase
        var yaml = """
                name: order api
                requests:
                  - name: slow report
                    timeout: "${vars.reportTimeout}"
                    requestDefinition:
                      url: "${env.baseUrl}/report"
                """;

        // when
        var suite = mapper.readValue(yaml, TestSuite.class);

        // then
        assertThat(suite.requests())
                .singleElement()
                .satisfies(request -> assertThat(request.timeout()).isEqualTo("${vars.reportTimeout}"));
    }

    @Test
    void constructor_keepsADeclaredPhaseAndHandlersOnARequest() {
        // given
        var handlers = new java.util.LinkedHashMap<String, String>();
        handlers.put("http", "mtls");

        // when
        var request = new Request("r", null, null, null, null, Phase.TEARDOWN, handlers, null, null, null);
        handlers.put("ftp", "other");

        // then - declared values survive, and the map is copied
        assertThat(request.phase()).isEqualTo(Phase.TEARDOWN);
        assertThat(request.requestHandlers()).containsOnlyKeys("http");
    }

    @Test
    void constructor_defaultsPhaseOnARequestToo() {
        // when
        var request = new Request("r", null, null, null, null, null, null, null, null, null);

        // then
        assertThat(request.phase()).isEqualTo(Phase.MAIN);
        assertThat(request.requestHandlers()).isEmpty();
    }

    @Test
    void bind_flattensNestedConstantsToDottedKeys() {
        // given
        var yaml = """
                name: s
                constants:
                  db:
                    host: db.internal
                    port: 5432
                """;

        // when
        var suite = mapper.readValue(yaml, TestSuite.class);

        // then - reachable as ${constants.db.host}, which looks up the whole dotted key
        assertThat(suite.constants()).containsOnlyKeys("db.host", "db.port").containsEntry("db.host", "db.internal");
    }

    @Test
    void constructor_rejectsATokenInConstantsNamingTheKeyButNotTheValue() {
        // given
        Map<String, Object> constants = Map.of("runId", "${__uuid}");

        // then - constants are literal; computing a value is what suite-level setVars is for
        assertThatThrownBy(() -> new TestSuite("s", null, constants, null, null, null, null, null, null, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants.runId")
                .hasMessageNotContaining("__uuid");
    }

    @Test
    void constructor_rejectsANestedConstantCollidingWithADottedOne() {
        // given
        var constants = new LinkedHashMap<String, Object>();
        constants.put("db", Map.of("host", "a"));
        constants.put("db.host", "b");

        // then
        assertThatThrownBy(() -> new TestSuite("s", null, constants, null, null, null, null, null, null, null))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("constants.db.host");
    }
}
