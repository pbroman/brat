package dev.pbroman.brat.openapi;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.openapi.OpenApiDocument.Field;
import dev.pbroman.brat.openapi.OpenApiDocument.MediaType;
import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.Parameter;
import dev.pbroman.brat.openapi.OpenApiDocument.Schema;
import dev.pbroman.brat.openapi.OpenApiDocument.SecurityScheme;
import dev.pbroman.brat.openapi.OpenApiDocument.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.node.StringNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenApiLoaderTest {

    private static final String HEAD = """
            openapi: 3.0.3
            info:
              title: Orders API
            """;

    private static OpenApiDocument load(String body) {
        return OpenApiLoader.load(HEAD + body);
    }

    private static Operation only(OpenApiDocument document) {
        assertThat(document.operations()).hasSize(1);
        return document.operations().getFirst();
    }

    // --- the document ---

    @Test
    void load_readsTheTitle() {
        // when
        var document = load("paths: {}\n");

        // then
        assertThat(document.title()).isEqualTo("Orders API");
    }

    @Test
    void load_leavesAMissingTitleNullAndEverythingElseEmpty() {
        // when
        var document = OpenApiLoader.load("openapi: 3.1.0\n");

        // then
        assertThat(document.title()).isNull();
        assertThat(document.servers()).isEmpty();
        assertThat(document.operations()).isEmpty();
        assertThat(document.security()).isEmpty();
        assertThat(document.securitySchemes()).isEmpty();
    }

    @Test
    void load_readsJsonAsWellAsYaml() {
        // given
        var json = """
                {
                  "openapi": "3.1.0",
                  "info": { "title": "Orders API" },
                  "paths": { "/health": { "get": { "summary": "health check" } } }
                }
                """;

        // when
        var document = OpenApiLoader.load(json);

        // then
        assertThat(document.title()).isEqualTo("Orders API");
        assertThat(only(document).summary()).isEqualTo("health check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"3.0.0", "3.0.3", "3.1.0", "3.1.1", "3.0", "3.1", "3.2.0", "3.2"})
    void load_acceptsOpenApi30To32(String version) {
        // when
        var document = OpenApiLoader.load("openapi: \"" + version + "\"\ninfo:\n  title: t\n");

        // then
        assertThat(document.title()).isEqualTo("t");
    }

    // --- servers ---

    @Test
    void load_readsTheServersInOrderWithTheirVariableDefaults() {
        // when
        var document = load("""
                servers:
                  - url: http://localhost:8080
                    description: local
                  - url: https://{region}.example.com:{port}
                    variables:
                      region:
                        default: eu
                        enum: [eu, us]
                      port:
                        default: "443"
                      unset:
                        enum: [a, b]
                """);

        // then
        assertThat(document.servers())
                .containsExactly(
                        new Server("http://localhost:8080", "local", Map.of()),
                        new Server("https://{region}.example.com:{port}", null, Map.of("region", "eu", "port", "443")));
        assertThat(document.servers().get(1).variableDefaults().keySet()).containsExactly("region", "port");
    }

    // --- operations ---

    @Test
    void load_readsEveryMethodInDocumentOrderAndIgnoresOtherPathItemKeys() {
        // when
        var document = load("""
                paths:
                  /b:
                    summary: not an operation
                    x-internal: true
                    trace: {}
                    patch: {}
                    head: {}
                    options: {}
                  /a:
                    delete: {}
                    post: {}
                    put: {}
                    get: {}
                """);

        // then
        assertThat(document.operations())
                .extracting(o -> o.method() + " " + o.path())
                .containsExactly(
                        "TRACE /b", "PATCH /b", "HEAD /b", "OPTIONS /b", "DELETE /a", "POST /a", "PUT /a", "GET /a");
    }

    @Test
    void load_readsAnOperationsIdSummaryAndTags() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    get:
                      operationId: listOrders
                      summary: list the orders
                      tags: [orders, reporting]
                """));

        // then
        assertThat(operation.operationId()).isEqualTo("listOrders");
        assertThat(operation.summary()).isEqualTo("list the orders");
        assertThat(operation.tags()).containsExactly("orders", "reporting");
    }

    @Test
    void load_leavesAnOperationsMissingFieldsNullOrEmpty() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    get: {}
                """));

        // then
        assertThat(operation.operationId()).isNull();
        assertThat(operation.summary()).isNull();
        assertThat(operation.tags()).isEmpty();
        assertThat(operation.parameters()).isEmpty();
        assertThat(operation.requestBody()).isEmpty();
        assertThat(operation.responseCodes()).isEmpty();
        assertThat(operation.security()).isNull();
    }

    @Test
    void load_readsTheResponseCodesAsWrittenAndInOrder() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    post:
                      responses:
                        "400": { description: invalid }
                        201: { description: created }
                        2XX: { description: fine }
                        default: { description: anything }
                """));

        // then
        assertThat(operation.responseCodes()).containsExactly("400", "201", "2XX", "default");
    }

    // --- parameters ---

    @Test
    void load_readsParametersWithThePathItemsFirstAndTheOperationsReplacingByNameAndIn() {
        // when
        var operation = only(load("""
                paths:
                  /orders/{orderId}:
                    parameters:
                      - { name: orderId, in: path }
                      - { name: verbose, in: query, required: false }
                      - { name: verbose, in: header }
                    get:
                      parameters:
                        - { name: expand, in: query }
                        - { name: verbose, in: query, required: true }
                """));

        // then
        assertThat(operation.parameters())
                .extracting(p -> p.in() + ":" + p.name() + ":" + p.required())
                .containsExactly(
                        "path:orderId:true", "query:verbose:true", "header:verbose:false", "query:expand:false");
    }

    @Test
    void load_takesTheFirstExampleAParameterHas() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    get:
                      parameters:
                        - name: a
                          in: query
                          example: fromExample
                          examples: { one: { value: fromExamples } }
                          schema: { example: fromSchema, default: fromDefault }
                        - name: b
                          in: query
                          examples: { one: { value: fromExamples }, two: { value: second } }
                          schema: { example: fromSchema, default: fromDefault }
                        - name: c
                          in: query
                          schema: { example: fromSchema, default: fromDefault }
                        - name: d
                          in: query
                          schema: { default: 10 }
                        - name: e
                          in: query
                          schema: { type: string }
                """));

        // then
        assertThat(operation.parameters())
                .extracting(p -> p.example() == null ? null : p.example().toString())
                .containsExactly("\"fromExample\"", "\"fromExamples\"", "\"fromSchema\"", "10", null);
    }

    @Test
    void load_treatsAPathParameterAsRequiredWhateverItSays() {
        // when
        var operation = only(load("""
                paths:
                  /orders/{orderId}:
                    get:
                      parameters:
                        - { name: orderId, in: path, required: false }
                """));

        // then
        assertThat(operation.parameters()).containsExactly(new Parameter("orderId", "path", true, null, null));
    }

    // --- request bodies ---

    @Test
    void load_readsTheRequestBodysMediaTypesInOrderWithTheirFirstExample() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    post:
                      requestBody:
                        content:
                          application/json:
                            example: { "item": "book", "quantity": 2 }
                            schema: { example: { "item": "ignored" } }
                          application/xml:
                            examples: { one: { value: "<order/>" } }
                          text/plain:
                            schema: { example: plain }
                          application/octet-stream: {}
                """));

        // then
        assertThat(operation.requestBody())
                .extracting(m -> m.type() + "="
                        + (m.example() == null ? null : m.example().toString()))
                .containsExactly(
                        "application/json={\"item\":\"book\",\"quantity\":2}",
                        "application/xml=\"<order/>\"",
                        "text/plain=\"plain\"",
                        "application/octet-stream=null");
    }

    @Test
    void load_readsTheSchemasPropertyNamesInOrder() {
        // when
        var operation = only(load("""
                paths:
                  /token:
                    post:
                      requestBody:
                        content:
                          application/x-www-form-urlencoded:
                            schema:
                              type: object
                              properties:
                                grant_type: { type: string }
                                client_id: { type: string }
                                scope: { type: string }
                """));

        // then
        assertThat(operation.requestBody().getFirst().properties()).containsExactly("grant_type", "client_id", "scope");
    }

    // --- security ---

    @Test
    void load_readsTheDocumentsSecurityAndTellsAnOperationsOwnFromNoneAndFromEmpty() {
        // when
        var document = load("""
                security:
                  - clientCredentials: [orders.read, orders.write]
                  - apiKey: []
                paths:
                  /a:
                    get: {}
                  /b:
                    get:
                      security: []
                  /c:
                    get:
                      security:
                        - basic: []
                """);

        // then
        assertThat(document.security())
                .containsExactly(
                        Map.of("clientCredentials", List.of("orders.read", "orders.write")),
                        Map.of("apiKey", List.of()));
        assertThat(document.operations())
                .extracting(Operation::security)
                .containsExactly(null, List.of(), List.of(Map.of("basic", List.of())));
    }

    @Test
    void load_readsTheSecuritySchemesOfEachKind() {
        // when
        var document = load("""
                components:
                  securitySchemes:
                    token:
                      type: http
                      scheme: Bearer
                    basic:
                      type: http
                      scheme: basic
                    key:
                      type: apiKey
                      in: header
                      name: X-Api-Key
                    clientCredentials:
                      type: oauth2
                      flows:
                        clientCredentials:
                          tokenUrl: https://login.example.com/oauth/token
                          scopes: {}
                    authCode:
                      type: oauth2
                      flows:
                        authorizationCode:
                          authorizationUrl: https://login.example.com/authorize
                          tokenUrl: https://login.example.com/oauth/token
                          scopes: {}
                    oidc:
                      type: openIdConnect
                      openIdConnectUrl: https://login.example.com/.well-known/openid-configuration
                """);

        // then
        assertThat(document.securitySchemes().keySet())
                .containsExactly("token", "basic", "key", "clientCredentials", "authCode", "oidc");
        assertThat(document.securitySchemes())
                .containsEntry("token", new SecurityScheme("http", "bearer", null, null, null))
                .containsEntry("basic", new SecurityScheme("http", "basic", null, null, null))
                .containsEntry("key", new SecurityScheme("apikey", null, "header", "X-Api-Key", null))
                .containsEntry(
                        "clientCredentials",
                        new SecurityScheme("oauth2", null, null, null, "https://login.example.com/oauth/token"))
                .containsEntry("authCode", new SecurityScheme("oauth2", null, null, null, null))
                .containsEntry("oidc", new SecurityScheme("openidconnect", null, null, null, null));
    }

    // --- $ref ---

    @Test
    void load_followsLocalReferencesWhereverItReads() {
        // when
        var operation = only(load("""
                paths:
                  /orders/{orderId}:
                    $ref: "#/components/pathItems/order"
                components:
                  pathItems:
                    order:
                      put:
                        parameters:
                          - $ref: "#/components/parameters/orderId"
                        requestBody:
                          $ref: "#/components/requestBodies/order"
                  parameters:
                    orderId:
                      name: orderId
                      in: path
                      schema:
                        $ref: "#/components/schemas/OrderId"
                  schemas:
                    OrderId:
                      example: "4711"
                    Order:
                      properties:
                        item: {}
                        quantity: {}
                  requestBodies:
                    order:
                      content:
                        application/json:
                          examples:
                            first:
                              $ref: "#/components/examples/anOrder"
                          schema:
                            $ref: "#/components/schemas/Order"
                  examples:
                    anOrder:
                      value: { "item": "book" }
                """));

        // then
        assertThat(operation.method()).isEqualTo("PUT");
        assertThat(operation.parameters().getFirst().example().toString()).isEqualTo("\"4711\"");
        assertThat(operation.requestBody().getFirst().example().toString()).isEqualTo("{\"item\":\"book\"}");
        assertThat(operation.requestBody().getFirst().properties()).containsExactly("item", "quantity");
    }

    @Test
    void load_followsAReferenceToAReference() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    get:
                      parameters:
                        - $ref: "#/components/parameters/alias"
                components:
                  parameters:
                    alias:
                      $ref: "#/components/parameters/status"
                    status:
                      name: status
                      in: query
                """));

        // then
        assertThat(operation.parameters()).extracting(Parameter::name).containsExactly("status");
    }

    @Test
    void load_followsAReferenceToASecurityScheme() {
        // when
        var document = load("""
                components:
                  securitySchemes:
                    token:
                      $ref: "#/components/securitySchemes/shared"
                    shared:
                      type: http
                      scheme: bearer
                """);

        // then
        assertThat(document.securitySchemes())
                .containsEntry("token", new SecurityScheme("http", "bearer", null, null, null));
    }

    @Test
    void load_ignoresAReferenceWhereNothingIsRead() {
        // when
        var operation = only(load("""
                paths:
                  /orders:
                    get:
                      responses:
                        "200":
                          content:
                            application/json:
                              schema:
                                $ref: "other-file.yaml#/Order"
                """));

        // then
        assertThat(operation.responseCodes()).containsExactly("200");
    }

    @Test
    void load_throwsForAnExternalReferenceItFollowsNamingItAndWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            get:
                              parameters:
                                - $ref: "common.yaml#/parameters/status"
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("common.yaml#/parameters/status")
                .hasMessageContaining("#/paths/~1orders/get/parameters/0")
                .hasMessageContaining("within the document");
    }

    @Test
    void load_throwsForAReferenceToNothingNamingItAndWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            post:
                              requestBody:
                                $ref: "#/components/requestBodies/missing"
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("#/components/requestBodies/missing")
                .hasMessageContaining("#/paths/~1orders/post/requestBody");
    }

    @Test
    void load_throwsForAReferenceThatLeadsBackToItself() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            get:
                              parameters:
                                - $ref: "#/components/parameters/a"
                        components:
                          parameters:
                            a:
                              $ref: "#/components/parameters/b"
                            b:
                              $ref: "#/components/parameters/a"
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("#/components/parameters/a");
    }

    // --- what is refused ---

    @ParameterizedTest
    @ValueSource(strings = {"", "  \n"})
    void load_throwsForBlankContent(String content) {
        // when / then
        assertThatThrownBy(() -> OpenApiLoader.load(content))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("The OpenAPI document is empty");
    }

    @Test
    void load_throwsForContentThatIsNeitherJsonNorYamlNamingThePositionButNotTheContent() {
        // when / then
        assertThatThrownBy(() -> OpenApiLoader.load("openapi: 3.0.3\npaths: [s3cr3t\n"))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The OpenAPI document is not valid YAML: ")
                .hasMessageContaining(" at line ")
                .hasMessageNotContaining("s3cr3t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"- a list\n", "just text\n", "info:\n  title: no version\n", "~\n"})
    void load_throwsForADocumentThatIsNotOpenApi(String content) {
        // when / then
        assertThatThrownBy(() -> OpenApiLoader.load(content))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("not an OpenAPI document");
    }

    @Test
    void load_throwsForSwagger2SayingToConvertIt() {
        // when / then
        assertThatThrownBy(() -> OpenApiLoader.load("swagger: \"2.0\"\ninfo:\n  title: old\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Swagger 2")
                .hasMessageContaining("OpenAPI 3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2.0", "3.3.0", "4.0.0", "3.10", "30"})
    void load_throwsForAnotherVersionNamingIt(String version) {
        // when / then
        assertThatThrownBy(() -> OpenApiLoader.load("openapi: \"" + version + "\"\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining(version);
    }

    @Test
    void load_throwsForAValueOfTheWrongShapeNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            get:
                              parameters: not-a-list
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("#/paths/~1orders/get/parameters");
    }

    @Test
    void load_throwsForAParameterWithoutANameNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            get:
                              parameters:
                                - in: query
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'name'")
                .hasMessageContaining("#/paths/~1orders/get/parameters/0");
    }

    @Test
    void load_throwsForAParameterWithoutAnInNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            get:
                              parameters:
                                - name: status
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'in'")
                .hasMessageContaining("#/paths/~1orders/get/parameters/0");
    }

    @Test
    void load_throwsForAServerWithoutAUrlNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        servers:
                          - description: local
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'url'")
                .hasMessageContaining("#/servers/0");
    }

    @Test
    void load_throwsForASecuritySchemeWithoutATypeNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        components:
                          securitySchemes:
                            token:
                              scheme: bearer
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'type'")
                .hasMessageContaining("#/components/securitySchemes/token");
    }

    @Test
    void load_throwsForAVersionThatIsNotText() {
        // when / then
        assertThatThrownBy(() -> OpenApiLoader.load("openapi: 3.1\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("must be text");
    }

    @Test
    void load_throwsWhereAMappingIsExpectedNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        servers:
                          - http://localhost:8080
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Expected a mapping")
                .hasMessageContaining("#/servers/0");
    }

    @Test
    void load_throwsWhereTextIsExpectedNamingWhere() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            get:
                              summary:
                                text: not like this
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("Expected text")
                .hasMessageContaining("#/paths/~1orders/get/summary");
    }

    @Test
    void load_readsAYamlDocumentWrittenInFlowStyle() {
        // when
        var document = OpenApiLoader.load("{openapi: 3.0.3, info: {title: Orders API}}");

        // then
        assertThat(document.title()).isEqualTo("Orders API");
    }

    @Test
    void load_throwsForBrokenJsonNamingThePositionButNotTheContent() {
        // when / then
        assertThatThrownBy(
                        () -> OpenApiLoader.load("{\n  \"openapi\": \"3.0.3\",\n  \"info\": {\"title\": \"s3cr3t\"\n"))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The OpenAPI document is not valid JSON: ")
                .hasMessageContaining(" at line ")
                .hasMessageNotContaining("s3cr3t");
    }

    @Test
    void load_percentDecodesAReferenceFirst() {
        // when
        var document = load("""
                paths:
                  /orders/{orderId}:
                    get:
                      summary: read an order
                  /orders/{orderId}/copy:
                    $ref: "#/paths/~1orders~1%7BorderId%7D"
                """);

        // then
        assertThat(document.operations())
                .extracting(Operation::summary)
                .containsExactly("read an order", "read an order");
    }

    @Test
    void load_throwsForAReferenceWithMalformedPercentEncoding() {
        // when / then
        assertThatThrownBy(() -> load("""
                        paths:
                          /orders:
                            $ref: "#/paths/~1x%zz"
                        """))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("#/paths/~1x%zz")
                .hasMessageContaining("points at nothing");
    }

    // --- reading the document ---

    private static Schema bodySchema(OpenApiDocument document) {
        return only(document).requestBody().getFirst().schema();
    }

    private static Field field(Schema schema, String name) {
        return schema.properties().stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String withBodySchema(String schema) {
        return "paths:\n  /orders:\n    post:\n      requestBody:\n        content:\n"
                + "          application/json:\n            schema:\n"
                + schema.indent(14);
    }

    @Test
    void load_throwsForADocumentOverTheSizeLimitBeforeParsingIt() {
        // given - one byte over 64 MB, and not even a document
        var content = "x".repeat(OpenApiLoader.MAX_MEGABYTES * 1024 * 1024 + 1);

        // then
        assertThatThrownBy(() -> OpenApiLoader.load(content))
                .isInstanceOf(BratException.class)
                .hasMessage("The OpenAPI document is 65 MB; at most 64 MB is read");
    }

    @Test
    void load_measuresTheSizeInCodePointsNotChars() {
        // given - 33 Mi emoji: 66 Mi chars, but only 33 MB of text as YAML counts it. The second line
        // is broken, so parsing fails at once instead of reading all of it
        var content = "openapi: 3.0.3\n  broken: here\n" + "\uD83D\uDE00".repeat(33 * 1024 * 1024);

        // then - past the size check, refused by the parser instead
        assertThatThrownBy(() -> OpenApiLoader.load(content))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The OpenAPI document is not valid YAML: ");
    }

    @Test
    void load_throwsForAJsonKeyWrittenTwiceNamingIt() {
        // then - as for YAML, the same spec in either format reads the same
        assertThatThrownBy(() -> OpenApiLoader.load("{\"openapi\": \"3.0.3\", \"paths\": {}, \"paths\": {}}"))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The OpenAPI document is not valid JSON: ")
                .hasMessageContaining("\"paths\"")
                .hasMessageContaining(" at line 1");
    }

    @Test
    void load_readsADocumentOverTheSuiteLimit() {
        // given - over the 3 MB a suite may be, well under 64 MB
        var description = "d".repeat(4 * 1024 * 1024);
        var content = HEAD + "  description: " + description + "\npaths: {}\n";

        // when
        var document = OpenApiLoader.load(content);

        // then
        assertThat(document.title()).isEqualTo("Orders API");
    }

    @Test
    void load_resolvesYamlAliases() {
        // given
        var body = """
                paths:
                  /a/{id}:
                    get:
                      parameters:
                        - &idParam
                          name: id
                          in: path
                          example: abc
                  /b/{id}:
                    get:
                      parameters:
                        - *idParam
                """;

        // when
        var document = load(body);

        // then
        assertThat(document.operations())
                .extracting(o -> o.parameters().getFirst().name())
                .containsExactly("id", "id");
    }

    @Test
    void load_resolvesYamlMergeKeys() {
        // given
        var body = """
                x-base: &base
                  in: query
                  required: true
                paths:
                  /orders:
                    get:
                      parameters:
                        - <<: *base
                          name: status
                """;

        // when
        var parameter = only(load(body)).parameters().getFirst();

        // then
        assertThat(parameter.name()).isEqualTo("status");
        assertThat(parameter.in()).isEqualTo("query");
        assertThat(parameter.required()).isTrue();
    }

    @Test
    void load_throwsForMoreThanFiftyAliases() {
        // given
        var body = "x-base: &a\n  k: v\nx-list:\n" + "  - *a\n".repeat(51) + "paths: {}\n";

        // then
        assertThatThrownBy(() -> load(body))
                .isInstanceOf(BratException.class)
                .hasMessageStartingWith("The OpenAPI document uses more than the 50 permitted aliases");
    }

    @Test
    void load_throwsForAYamlKeyWrittenTwiceNamingItAndWhere() {
        // then
        assertThatThrownBy(() -> load("paths: {}\npaths: {}\n"))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'paths'")
                .hasMessageContaining("line 5");
    }

    @Test
    void load_readsTheQueryMethod() {
        // when
        var operation = only(load("paths:\n  /search:\n    query:\n      summary: search\n"));

        // then
        assertThat(operation.method()).isEqualTo("QUERY");
    }

    @Test
    void load_readsAnOperationsOwnServersElseItsPathItems() {
        // given
        var body = """
                servers:
                  - url: https://api.example.com
                paths:
                  /upload:
                    servers:
                      - url: https://uploads.example.com
                    post:
                      summary: path's
                    put:
                      summary: own
                      servers:
                        - url: https://direct.example.com
                  /plain:
                    get:
                      summary: none
                """;

        // when
        var operations = load(body).operations();

        // then
        assertThat(operations)
                .extracting(o -> o.summary() + " "
                        + o.servers().stream().map(Server::url).toList())
                .containsExactly("path's [https://uploads.example.com]", "own [https://direct.example.com]", "none []");
    }

    @Test
    void load_namesWhatIsNotImported() {
        // given
        var body = """
                webhooks:
                  orderShipped: {}
                  orderCancelled: {}
                paths:
                  /orders:
                    additionalOperations:
                      COPY: {}
                      MOVE: {}
                    get:
                      summary: list
                """;

        // when
        var document = load(body);

        // then
        assertThat(document.notImported())
                .containsExactly(
                        "webhooks: orderShipped, orderCancelled", "additionalOperations on /orders: COPY, MOVE");
        assertThat(document.operations()).hasSize(1);
    }

    @Test
    void load_namesNothingNotImportedWhenThereIsNothing() {
        assertThat(load("paths: {}\n").notImported()).isEmpty();
        assertThat(load("webhooks: {}\npaths:\n  /o:\n    additionalOperations: {}\n")
                        .notImported())
                .isEmpty();
    }

    @Test
    void load_marksAMediaExampleFromValueOrDataValueAsNotSerialized() {
        for (var key : new String[] {"value", "dataValue"}) {
            // given - both present: the earlier key wins, so the example is not the serialized one
            var body = "paths:\n  /o:\n    post:\n      requestBody:\n        content:\n"
                    + "          application/json:\n            examples:\n              one:\n"
                    + "                " + key + ": {a: 1}\n                serializedValue: '{\"a\":2}'\n";

            // when
            var media = only(load(body)).requestBody().getFirst();

            // then
            assertThat(media.serialized()).as(key).isFalse();
            assertThat(media.example().toString()).as(key).isEqualTo("{\"a\":1}");
        }
    }

    @Test
    void load_readsABooleanSchemaAsAnEmptyOne() {
        // when - 3.1 allows true and false as schemas
        var a = field(bodySchema(load(withBodySchema("properties:\n  a: true\n"))), "a")
                .schema();

        // then
        assertThat(a).isEqualTo(new Schema(null, null, null, List.of(), List.of(), null));
    }

    @Test
    void load_endsACycleOfAllOfs() {
        // given
        var body = withBodySchema("$ref: '#/components/schemas/A'\n") + """
                components:
                  schemas:
                    A:
                      allOf: [{$ref: '#/components/schemas/B'}]
                      properties: {a: {type: string}}
                    B:
                      allOf: [{$ref: '#/components/schemas/A'}]
                      properties: {b: {type: string}}
                """;

        // when
        var read = bodySchema(load(body));

        // then
        assertThat(read.properties()).extracting(Field::name).containsExactly("b", "a");
    }

    @Test
    void load_infersTheTypeWhenATypeListHoldsOnlyNull() {
        assertThat(bodySchema(load(withBodySchema("type: [\"null\"]\nproperties: {a: {}}\n")))
                        .type())
                .isEqualTo("object");
    }

    @Test
    void load_leavesStructuresOutOfAnEnum() {
        assertThat(bodySchema(load(withBodySchema("enum: [A, {b: 1}, [c], null, 3]\n")))
                        .enumValues())
                .containsExactly("A", "3");
    }

    @Test
    void load_ignoresASchemasKeywordsOfTheWrongShape() {
        // given - real specs carry these: required: true is Spotify's image upload
        var schema = """
                type: {not: text}
                required: true
                enum: OPEN
                allOf: none
                oneOf: [just text]
                items: [a list]
                properties:
                  a: {type: string, required: [x], properties: [wrong]}
                """;

        // when
        var read = bodySchema(load(withBodySchema(schema)));

        // then - the readable parts are read, the rest is absent
        assertThat(read.type()).isEqualTo("object");
        assertThat(read.enumValues()).isEmpty();
        assertThat(read.items()).isNull();
        assertThat(read.properties()).extracting(Field::name).containsExactly("a");
        assertThat(field(read, "a").required()).isFalse();
        assertThat(field(read, "a").schema().properties()).isEmpty();
    }

    @Test
    void load_stillRefusesAnExternalReferenceInsideASchema() {
        assertThatThrownBy(() -> load(withBodySchema("properties:\n  a: {$ref: 'other.yaml#/A'}\n")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("other.yaml#/A");
    }

    @Test
    void load_ignoresASchemasExamplesThatIsNotAList() {
        // given - a 3.0-era mistake: a mapping of named examples on a schema
        var read = bodySchema(load(withBodySchema("type: string\nexamples: {one: x}\n")));

        // then
        assertThat(read.example()).isNull();
    }

    @Test
    void load_takesAnExamplesDataValueBeforeItsValue() {
        // given - 3.2's dataValue, beside 3.1's value
        var body = """
                paths:
                  /orders:
                    get:
                      parameters:
                        - name: status
                          in: query
                          examples:
                            open:
                              dataValue: OPEN
                              value: LEGACY
                """;

        // when
        var parameter = only(load(body)).parameters().getFirst();

        // then
        assertThat(parameter.example().asString()).isEqualTo("OPEN");
    }

    @Test
    void load_takesAMediaTypesSerializedValueAsSerializedText() {
        // given
        var body = """
                paths:
                  /orders:
                    post:
                      requestBody:
                        content:
                          application/json:
                            examples:
                              one:
                                serializedValue: '{"item":"book"}'
                """;

        // when
        var media = only(load(body)).requestBody().getFirst();

        // then
        assertThat(media.example().asString()).isEqualTo("{\"item\":\"book\"}");
        assertThat(media.serialized()).isTrue();
    }

    @Test
    void load_marksAnExampleNotFromSerializedValueAsNotSerialized() {
        // when
        var media = only(load(withBodySchema("type: string\nexample: x\n")))
                .requestBody()
                .getFirst();

        // then
        assertThat(media.serialized()).isFalse();
    }

    @Test
    void load_ignoresAParametersSerializedValue() {
        // given
        var body = """
                paths:
                  /orders:
                    get:
                      parameters:
                        - name: status
                          in: query
                          examples:
                            open:
                              serializedValue: status=OPEN
                """;

        // when
        var parameter = only(load(body)).parameters().getFirst();

        // then
        assertThat(parameter.example()).isNull();
    }

    @Test
    void load_takesTheFirstOfASchemasExamplesList() {
        // given - JSON Schema's examples, a list, as 3.1 writes it
        var body = """
                paths:
                  /orders/{id}:
                    get:
                      parameters:
                        - name: id
                          in: path
                          schema:
                            type: string
                            examples: [first, second]
                """;

        // when
        var parameter = only(load(body)).parameters().getFirst();

        // then
        assertThat(parameter.example().asString()).isEqualTo("first");
    }

    @Test
    void load_readsASchemasTypeExampleAndEnum() {
        // when
        var schema = bodySchema(load(withBodySchema("type: string\nexample: OPEN\nenum: [OPEN, 2, null]\n")));

        // then
        assertThat(schema.type()).isEqualTo("string");
        assertThat(schema.example().asString()).isEqualTo("OPEN");
        assertThat(schema.enumValues()).containsExactly("OPEN", "2");
    }

    @Test
    void load_takesTheFirstTypeThatIsNotNullFromAList() {
        assertThat(bodySchema(load(withBodySchema("type: [\"null\", integer]\n")))
                        .type())
                .isEqualTo("integer");
    }

    @Test
    void load_infersAnObjectOrArrayTypeWhenNoneIsWritten() {
        // then
        assertThat(bodySchema(load(withBodySchema("properties:\n  a: {}\n"))).type())
                .isEqualTo("object");
        assertThat(bodySchema(load(withBodySchema("items: {type: string}\n"))).type())
                .isEqualTo("array");
        assertThat(bodySchema(load(withBodySchema("description: anything\n"))).type())
                .isNull();
    }

    @Test
    void load_readsASchemasPropertiesInOrderWithRequiredAndTheirOwnSchemas() {
        // given
        var schema = """
                type: object
                required: [item]
                properties:
                  item: {type: string, example: book}
                  quantity: {type: integer}
                """;

        // when
        var read = bodySchema(load(withBodySchema(schema)));

        // then
        assertThat(read.properties()).extracting(Field::name).containsExactly("item", "quantity");
        assertThat(read.properties()).extracting(Field::required).containsExactly(true, false);
        assertThat(field(read, "item").schema().example().asString()).isEqualTo("book");
        assertThat(field(read, "quantity").schema().type()).isEqualTo("integer");
    }

    @Test
    void load_followsReferencesInsideASchema() {
        // given
        var body = withBodySchema("$ref: '#/components/schemas/Order'\n") + """
                components:
                  schemas:
                    Order:
                      properties:
                        customer: {$ref: '#/components/schemas/Customer'}
                    Customer:
                      properties:
                        name: {type: string, example: Ada}
                """;

        // when
        var read = bodySchema(load(body));

        // then
        assertThat(field(field(read, "customer").schema(), "name")
                        .schema()
                        .example()
                        .asString())
                .isEqualTo("Ada");
    }

    @Test
    void load_mergesAllOfMembersInOrderTheSchemasOwnLast() {
        // given
        var schema = """
                allOf:
                  - required: [id]
                    properties:
                      id: {type: string}
                      note: {type: string, example: first}
                  - properties:
                      note: {type: string, example: second}
                required: [note]
                properties:
                  own: {type: boolean}
                """;

        // when
        var read = bodySchema(load(withBodySchema(schema)));

        // then
        assertThat(read.properties()).extracting(Field::name).containsExactly("id", "note", "own");
        assertThat(field(read, "id").required()).isTrue();
        assertThat(field(read, "note").required()).isTrue();
        assertThat(field(read, "note").schema().example().asString()).isEqualTo("second");
    }

    @Test
    void load_readsOneOfAndAnyOfAsTheirFirstAlternative() {
        for (var keyword : new String[] {"oneOf", "anyOf"}) {
            // given
            var schema = keyword + ":\n  - properties: {a: {type: string}}\n  - properties: {b: {type: string}}\n";

            // when
            var read = bodySchema(load(withBodySchema(schema)));

            // then
            assertThat(read.properties()).as(keyword).extracting(Field::name).containsExactly("a");
        }
    }

    @Test
    void load_readsAnArraysItems() {
        // when
        var read = bodySchema(load(withBodySchema("type: array\nitems: {type: string, example: x}\n")));

        // then
        assertThat(read.items().type()).isEqualTo("string");
        assertThat(read.items().example().asString()).isEqualTo("x");
    }

    @Test
    void load_stopsNestingAfterThreeLevels() {
        // given - a, then b, then c: c is the third level, and keeps its type but not its properties
        var schema = """
                properties:
                  a:
                    properties:
                      b:
                        properties:
                          c:
                            type: object
                            example: {}
                            properties:
                              d: {type: string}
                """;

        // when
        var c = field(
                        field(
                                        field(bodySchema(load(withBodySchema(schema))), "a")
                                                .schema(),
                                        "b")
                                .schema(),
                        "c")
                .schema();

        // then
        assertThat(c.type()).isEqualTo("object");
        assertThat(c.example()).isNotNull();
        assertThat(c.properties()).isEmpty();
    }

    @Test
    void load_countsAnArraysItemsAsALevel() {
        // given - a, then its items, then b: b is the third level
        var schema = """
                properties:
                  a:
                    type: array
                    items:
                      properties:
                        b:
                          type: array
                          items: {type: string}
                """;

        // when
        var b = field(
                        field(bodySchema(load(withBodySchema(schema))), "a")
                                .schema()
                                .items(),
                        "b")
                .schema();

        // then
        assertThat(b.type()).isEqualTo("array");
        assertThat(b.items()).isNull();
    }

    @Test
    void load_endsARecursiveSchema() {
        // given
        var body = withBodySchema("$ref: '#/components/schemas/Node'\n") + """
                components:
                  schemas:
                    Node:
                      properties:
                        child: {$ref: '#/components/schemas/Node'}
                """;

        // when
        var read = bodySchema(load(body));

        // then
        assertThat(field(field(read, "child").schema(), "child").schema().properties())
                .hasSize(1);
        assertThat(field(field(field(read, "child").schema(), "child").schema(), "child")
                        .schema()
                        .properties())
                .isEmpty();
    }

    @Test
    void load_readsAnEmptySchemaForAPropertyWrittenAsBraces() {
        // when
        var a = field(bodySchema(load(withBodySchema("properties:\n  a: {}\n"))), "a")
                .schema();

        // then
        assertThat(a).isEqualTo(new Schema(null, null, null, List.of(), List.of(), null));
    }

    @Test
    void load_givesAParameterWithoutASchemaNone() {
        assertThat(only(load("paths:\n  /o/{id}:\n    get:\n      parameters:\n" + "        - {name: id, in: path}\n"))
                        .parameters()
                        .getFirst()
                        .schema())
                .isNull();
    }

    // --- the model's derived values ---

    private static Schema schema(String type, List<String> enumValues, Schema items) {
        return new Schema(type, null, null, enumValues, List.of(), items);
    }

    private static Parameter parameter(String name, String in, Schema schema, String example) {
        return new Parameter(name, in, false, example == null ? null : StringNode.valueOf(example), schema);
    }

    @Test
    void identity_isEqualForTheSameInTypeAndEnum() {
        // given - different names, examples and required: none of them count
        var a = parameter("status", "query", schema("string", List.of("A", "B"), null), "A");
        var b = new Parameter("other", "query", true, null, schema("string", List.of("A", "B"), null));

        // then
        assertThat(a.identity()).isEqualTo(b.identity());
    }

    @Test
    void identity_differsByInTypeEnumOrEnumOrder() {
        // given
        var base = parameter("s", "query", schema("string", List.of("A", "B"), null), null);
        var others = List.of(
                parameter("s", "path", schema("string", List.of("A", "B"), null), null),
                parameter("s", "query", schema("integer", List.of("A", "B"), null), null),
                parameter("s", "query", schema("string", List.of("A", "C"), null), null),
                parameter("s", "query", schema("string", List.of("B", "A"), null), null),
                parameter("s", "query", schema("string", List.of(), null), null));

        // then
        for (var other : others) {
            assertThat(other.identity()).as(other.toString()).isNotEqualTo(base.identity());
        }
    }

    @Test
    void identity_differsByFormat() {
        // given - DigitalOcean's id: a uuid on some operations, free text on others
        var uuid = parameter("id", "path", new Schema("string", "uuid", null, List.of(), List.of(), null), null);
        var text = parameter("id", "path", schema("string", List.of(), null), null);
        var uuidItems = parameter(
                "ids",
                "query",
                schema("array", List.of(), new Schema("string", "uuid", null, List.of(), List.of(), null)),
                null);
        var textItems = parameter("ids", "query", schema("array", List.of(), schema("string", List.of(), null)), null);

        // then
        assertThat(uuid.identity()).isNotEqualTo(text.identity());
        assertThat(uuidItems.identity()).isNotEqualTo(textItems.identity());
    }

    @Test
    void load_readsASchemasFormat() {
        assertThat(bodySchema(load(withBodySchema("type: string\nformat: uuid\n")))
                        .format())
                .isEqualTo("uuid");
        assertThat(bodySchema(load(withBodySchema("type: string\n"))).format()).isNull();
    }

    @Test
    void identity_differsByTheItemsOfAnArray() {
        // given
        var strings = parameter("ids", "query", schema("array", List.of(), schema("string", List.of(), null)), null);
        var numbers = parameter("ids", "query", schema("array", List.of(), schema("integer", List.of(), null)), null);
        var enumed = parameter("ids", "query", schema("array", List.of(), schema("string", List.of("a"), null)), null);

        // then
        assertThat(strings.identity()).isNotEqualTo(numbers.identity()).isNotEqualTo(enumed.identity());
    }

    @Test
    void identity_ignoresEveryOtherPartOfTheSchema() {
        // given
        var plain = parameter("s", "query", schema("string", List.of(), null), null);
        var rich = parameter(
                "s",
                "query",
                new Schema(
                        "string",
                        null,
                        StringNode.valueOf("x"),
                        List.of(),
                        List.of(new Field("f", true, schema("string", List.of(), null))),
                        null),
                null);

        // then
        assertThat(plain.identity()).isEqualTo(rich.identity());
    }

    @Test
    void identity_ofAParameterWithoutASchemaMatchesOnlyAnotherWithout() {
        // given
        var none = parameter("s", "query", null, null);
        var alsoNone = parameter("t", "query", null, "x");
        var typed = parameter("s", "query", schema("string", List.of(), null), null);

        // then
        assertThat(none.identity()).isEqualTo(alsoNone.identity()).isNotEqualTo(typed.identity());
    }

    @Test
    void enumValues_areTheSchemasElseTheItemsElseNone() {
        // then
        assertThat(parameter("s", "query", schema("string", List.of("A"), null), null)
                        .enumValues())
                .containsExactly("A");
        assertThat(parameter("s", "query", schema("array", List.of(), schema("string", List.of("x"), null)), null)
                        .enumValues())
                .containsExactly("x");
        assertThat(parameter("s", "query", null, null).enumValues()).isEmpty();
        assertThat(parameter("s", "query", schema("string", List.of(), null), null)
                        .enumValues())
                .isEmpty();
    }

    @Test
    void properties_areTheSchemasFieldNamesInOrder() {
        // given
        var schema = new Schema(
                "object",
                null,
                null,
                List.of(),
                List.of(
                        new Field("b", false, schema(null, List.of(), null)),
                        new Field("a", true, schema(null, List.of(), null))),
                null);

        // then
        assertThat(new MediaType("application/json", null, false, schema).properties())
                .containsExactly("b", "a");
        assertThat(new MediaType("application/json", null, false, null).properties())
                .isEmpty();
    }
}
