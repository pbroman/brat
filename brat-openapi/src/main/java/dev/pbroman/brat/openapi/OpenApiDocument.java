package dev.pbroman.brat.openapi;

import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * An OpenAPI document reduced to what the importer maps, every {@code $ref} it needed already
 * followed. Lists keep the document's order, and no component is {@code null} unless it says so.
 *
 * @param title {@code info.title}, or {@code null} if the document has none
 * @param servers the {@code servers}, empty if there are none
 * @param operations every operation, paths in document order and the methods of each path as written
 * @param security the document's default {@code security}: one map per alternative, from scheme name
 *        to scopes; empty if the document declares none
 * @param securitySchemes {@code components.securitySchemes} by name, in document order
 */
record OpenApiDocument(
        String title,
        List<Server> servers,
        List<Operation> operations,
        List<Map<String, List<String>>> security,
        Map<String, SecurityScheme> securitySchemes) {

    /**
     * One {@code servers} entry.
     *
     * @param url the url as written, variables unreplaced
     * @param description its description, or {@code null}
     * @param variableDefaults each server variable's {@code default}, in document order; a variable
     *        without one is left out
     */
    record Server(String url, String description, Map<String, String> variableDefaults) {}

    /**
     * One operation: a method under a path.
     *
     * @param method the method, upper case ({@code GET})
     * @param path the path as written ({@code /orders/{orderId}})
     * @param operationId its {@code operationId}, or {@code null}
     * @param summary its {@code summary}, or {@code null}
     * @param tags its tags in order, empty if it has none
     * @param parameters the path item's parameters and the operation's own, one replacing the other where
     *        both have the same {@code name} and {@code in}; the path item's come first, in order, then
     *        the operation's that replace none
     * @param requestBody the request body's media types in order, empty if it has no body
     * @param responseCodes the keys of {@code responses} as written ({@code 200}, {@code 2XX},
     *        {@code default}), in order; empty if there are none
     * @param security the operation's own {@code security}, as {@link OpenApiDocument#security()};
     *        {@code null} if it declares none, so the document's applies, and empty for
     *        {@code security: []}
     */
    record Operation(
            String method,
            String path,
            String operationId,
            String summary,
            List<String> tags,
            List<Parameter> parameters,
            List<MediaType> requestBody,
            List<String> responseCodes,
            List<Map<String, List<String>>> security) {}

    /**
     * One parameter.
     *
     * @param name its name
     * @param in where it goes: {@code path}, {@code query}, {@code header} or {@code cookie}
     * @param required whether it is required; always {@code true} for a path parameter
     * @param example the first of its {@code example}, the {@code value} of its first {@code examples}
     *        entry, its schema's {@code example}, its schema's {@code default}; {@code null} if it has none
     */
    record Parameter(String name, String in, boolean required, JsonNode example) {}

    /**
     * One media type of a request body.
     *
     * @param type the media type as written ({@code application/json})
     * @param example the first of its {@code example}, the {@code value} of its first {@code examples}
     *        entry, its schema's {@code example}; {@code null} if it has none
     * @param properties the names of its schema's {@code properties}, in order; empty if the schema has
     *        none
     */
    record MediaType(String type, JsonNode example, List<String> properties) {}

    /**
     * One security scheme.
     *
     * @param type its {@code type}, lower case ({@code http}, {@code apikey}, {@code oauth2},
     *        {@code openidconnect}, {@code mutualtls})
     * @param scheme for {@code http}, its {@code scheme} in lower case ({@code bearer}); otherwise
     *        {@code null}
     * @param in for {@code apikey}, where the key goes ({@code header}, {@code query}, {@code cookie});
     *        otherwise {@code null}
     * @param name for {@code apikey}, the header, query or cookie name; otherwise {@code null}
     * @param clientCredentialsTokenUrl for {@code oauth2} with a client-credentials flow, its
     *        {@code tokenUrl}; otherwise {@code null}
     */
    record SecurityScheme(String type, String scheme, String in, String name, String clientCredentialsTokenUrl) {}
}
