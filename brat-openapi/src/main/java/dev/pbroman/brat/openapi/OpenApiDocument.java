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
 * @param notImported what the document holds that is not imported, one entry per kind, in document
 *        order: {@code webhooks: orderShipped, orderCancelled} for the top-level {@code webhooks}, and
 *        {@code additionalOperations on /orders: COPY, MOVE} for each path item declaring some; empty
 *        if there is nothing of either
 */
record OpenApiDocument(
        String title,
        List<Server> servers,
        List<Operation> operations,
        List<Map<String, List<String>>> security,
        Map<String, SecurityScheme> securitySchemes,
        List<String> notImported) {

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
     * @param servers the servers for this operation alone: its own {@code servers}, else its path
     *        item's; empty when neither declares any, so the document's apply
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
            List<Map<String, List<String>>> security,
            List<Server> servers) {}

    /**
     * One parameter.
     *
     * @param name its name
     * @param in where it goes: {@code path}, {@code query}, {@code header} or {@code cookie}
     * @param required whether it is required; always {@code true} for a path parameter
     * @param example the first of its {@code example}; its first {@code examples} entry's
     *        {@code dataValue}, then {@code value}; its schema's {@code example}, then the first of its
     *        schema's {@code examples} list; its schema's {@code default}. {@code null} if it has none
     * @param schema its {@code schema}, or {@code null} if it has none
     */
    record Parameter(String name, String in, boolean required, JsonNode example, Schema schema) {

        /**
         * What makes two parameters of the same name the same parameter: equal for two parameters
         * exactly when they have the same {@code in}, the same schema {@link Schema#type() type} and
         * {@link Schema#format() format}, the same {@link Schema#enumValues() enum} in the same order,
         * and — for an array — the same type, format and enum of its items. The name, the example,
         * {@code required} and every other part of the schema do not count. A parameter without a schema
         * has a schema type, format, enum and items of none.
         *
         * @return the identity, to compare with {@code equals}; its text is otherwise unspecified;
         *         never {@code null}
         */
        String identity() {
            var items = schema == null ? null : schema.items();
            return String.join("|", in, shapeOf(schema), shapeOf(items));
        }

        /** A schema's type and enum as text, {@code -} for no schema. */
        private static String shapeOf(Schema schema) {
            return schema == null ? "-" : schema.type() + "/" + schema.format() + schema.enumValues();
        }

        /**
         * The values the parameter may take: its schema's {@link Schema#enumValues() enum}, or, for an
         * array, its items'.
         *
         * @return the values in order; empty if neither has an enum
         */
        List<String> enumValues() {
            if (schema == null) {
                return List.of();
            }
            if (!schema.enumValues().isEmpty() || schema.items() == null) {
                return schema.enumValues();
            }
            return schema.items().enumValues();
        }
    }

    /**
     * One media type of a request body.
     *
     * @param type the media type as written ({@code application/json})
     * @param example the first of its {@code example}; its first {@code examples} entry's
     *        {@code dataValue}, then {@code value}, then {@code serializedValue}; its schema's
     *        {@code example}, then the first of its schema's {@code examples} list. {@code null} if it
     *        has none
     * @param serialized whether {@code example} came from {@code serializedValue}, and so is text
     *        already in the media type's own form, to be sent as it is
     * @param schema its {@code schema}, or {@code null} if it has none
     */
    record MediaType(String type, JsonNode example, boolean serialized, Schema schema) {

        /**
         * The names of its schema's properties.
         *
         * @return the names in order; empty if there is no schema or it has no properties
         */
        List<String> properties() {
            return schema == null
                    ? List.of()
                    : schema.properties().stream().map(Field::name).toList();
        }
    }

    /**
     * A schema, reduced to what the importer builds values from, every {@code $ref} followed.
     * <p>
     * Read as written, except: {@code allOf} members are merged into it in order, their properties
     * and {@code required} names added to its own and a later property replacing an earlier one of the
     * same name, the schema's own properties last; a {@code oneOf} or {@code anyOf} is read as its
     * first alternative, merged the same way. <strong>Nesting stops at three levels</strong>: a
     * property of a property of a property — and an array's items count as a level — keeps its type,
     * example and enum but has no properties or items of its own, so a recursive schema ends. A
     * cycle of {@code allOf}s ends too: a member already being merged is skipped. A boolean schema
     * ({@code true} or {@code false}) reads as an empty one.
     * <p>
     * <strong>A schema's mistakes are ignored, not refused</strong>, because a schema only feeds the
     * values a scaffold suggests: a keyword of the wrong shape — {@code required: true},
     * {@code enum: OPEN}, a {@code type}, {@code properties}, {@code items}, {@code allOf} or an
     * alternative that is not what the specification says — reads as if it were absent. A
     * {@code $ref} is still followed, and refused, as everywhere else.
     *
     * @param type its {@code type}; for a list of types, the first that is not {@code null}; absent, or
     *        a list holding no other: {@code object} if it has {@code properties}, {@code array} if it
     *        has {@code items}, else {@code null}
     * @param format its {@code format} ({@code uuid}, {@code date-time}), or {@code null} if it has none
     * @param example its {@code example}, else the first of its {@code examples} list; {@code null} if
     *        it has neither, and an {@code examples} that is not a list does not count
     * @param enumValues its {@code enum} values as text, in order, a {@code null}, a mapping or a list
     *        left out; empty if it has none
     * @param properties its properties in order; empty if it has none or nesting stopped
     * @param items for an array, its {@code items}; {@code null} if it has none or nesting stopped
     */
    record Schema(
            String type,
            String format,
            JsonNode example,
            List<String> enumValues,
            List<Field> properties,
            Schema items) {}

    /**
     * One property of a schema.
     *
     * @param name the property's name
     * @param required whether the schema's {@code required} names it
     * @param schema the property's schema; never {@code null}, an empty schema for {@code {}}
     */
    record Field(String name, boolean required, Schema schema) {}

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
