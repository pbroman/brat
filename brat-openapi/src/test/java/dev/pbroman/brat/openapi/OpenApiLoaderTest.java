package dev.pbroman.brat.openapi;

import java.util.List;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.Parameter;
import dev.pbroman.brat.openapi.OpenApiDocument.SecurityScheme;
import dev.pbroman.brat.openapi.OpenApiDocument.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    @ValueSource(strings = {"3.0.0", "3.0.3", "3.1.0", "3.1.1", "3.0", "3.1"})
    void load_acceptsOpenApi30And31(String version) {
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
        assertThat(operation.parameters()).containsExactly(new Parameter("orderId", "path", true, null));
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
                .hasMessageContaining("line")
                .hasMessageNotContaining("s3cr3t");
    }

    @ParameterizedTest
    @ValueSource(strings = {"- a list\n", "just text\n", "info:\n  title: no version\n"})
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
    @ValueSource(strings = {"2.0", "3.2.0", "4.0.0", "3.10", "30"})
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
                .hasMessageContaining("neither JSON nor YAML")
                .hasMessageContaining("line")
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
}
