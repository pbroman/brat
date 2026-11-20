package dev.pbroman.brat.openapi;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.JacksonUtils;
import dev.pbroman.brat.openapi.OpenApiDocument.MediaType;
import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.Parameter;
import dev.pbroman.brat.openapi.OpenApiDocument.SecurityScheme;
import dev.pbroman.brat.openapi.OpenApiDocument.Server;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/**
 * Parses the content of an OpenAPI 3.0 or 3.1 document into an {@link OpenApiDocument}, following the
 * {@code $ref}s within the document that the importer needs.
 * <p>
 * It holds no state, so it may be called from several threads at once.
 */
final class OpenApiLoader {

    private static final YAMLMapper YAML = YAMLMapper.builder().build();

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Set<String> METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

    private static final Pattern SUPPORTED_VERSION = Pattern.compile("3\\.[01](\\..*)?");

    private static final String REF = "$ref";

    private OpenApiLoader() {
        // static helpers only
    }

    /**
     * Parses {@code content}, JSON or YAML, recognised by the content alone.
     * <p>
     * <strong>What is read</strong> is only what {@link OpenApiDocument} holds; every other key is
     * ignored, extensions ({@code x-…}) included. Of a path item, the keys {@code get}, {@code put},
     * {@code post}, {@code delete}, {@code options}, {@code head}, {@code patch} and {@code trace} are
     * operations, and {@code parameters} applies to each of them.
     * <p>
     * <strong>A {@code $ref}</strong> is followed wherever the loader reads a path item, a parameter, a
     * request body, an example, a schema or a security scheme, and a reference that leads to another
     * reference is followed on. Only references within the document ({@code #/…}, a JSON pointer) are
     * followed, the pointer percent-decoded first, as a URI fragment is ({@code %7B} is an opening
     * brace). A {@code $ref} where nothing is read — under a response's schema, say — is never looked
     * at, so it is not refused whatever it points to.
     * <p>
     * A location in a message is a JSON pointer into the document, as a {@code $ref} writes it
     * ({@code #/paths/~1orders/get/parameters/0}).
     *
     * @param content the document; must not be {@code null}
     * @return the document, reduced
     * @throws dev.pbroman.brat.core.exception.BratException
     *         <ul>
     *           <li>if {@code content} is blank: "The OpenAPI document is empty";</li>
     *           <li>if it is neither JSON nor YAML, with the line and column but none of the content;</li>
     *           <li>if its top is not a mapping, or has neither {@code openapi} nor {@code swagger}:
     *               "not an OpenAPI document";</li>
     *           <li>if it has {@code swagger}: it names Swagger 2 and says to convert it to OpenAPI 3
     *               first;</li>
     *           <li>if {@code openapi} is not text starting {@code 3.0} or {@code 3.1} followed by the end
     *               or a {@code .}, naming the version found;</li>
     *           <li>for a {@code $ref} it follows that does not start with {@code #/}, naming the
     *               reference and its location, and saying only references within the document are
     *               read;</li>
     *           <li>for a {@code $ref} it follows that points at nothing, or whose percent-encoding is
     *               malformed, naming the reference and its location;</li>
     *           <li>for a {@code $ref} that leads back to itself, naming the reference;</li>
     *           <li>where it reads a mapping, a list or text and finds something else, naming the
     *               location;</li>
     *           <li>for a parameter without a {@code name} or an {@code in}, a server without a
     *               {@code url}, or a security scheme without a {@code type}, naming the location.</li>
     *         </ul>
     */
    static OpenApiDocument load(String content) {
        if (StringUtils.isBlank(content)) {
            throw new BratException("The OpenAPI document is empty");
        }
        var root = parse(content);
        if (!root.isObject() || (!root.has("openapi") && !root.has("swagger"))) {
            throw new BratException("This is not an OpenAPI document: it has no 'openapi' key at the top");
        }
        if (root.has("swagger")) {
            throw new BratException("This is a Swagger 2 document, which is not read; convert it to OpenAPI 3 first");
        }
        var version = root.get("openapi");
        if (!version.isString()) {
            throw new BratException("The 'openapi' version must be text, as in openapi: \"3.1.0\"; found " + version);
        }
        if (!SUPPORTED_VERSION.matcher(version.asString()).matches()) {
            throw new BratException(
                    "OpenAPI " + version.asString() + " is not supported; only OpenAPI 3.0 and 3.1 are read");
        }
        return new Reading(root).document();
    }

    /**
     * Parses content starting with an opening brace as JSON first, falling back to YAML for a flow-style YAML
     * document; JSON is tried first because JSON indented with tabs is not YAML. A failure is reported
     * at the position the first parser tried gave.
     */
    private static JsonNode parse(String content) {
        if (!content.stripLeading().startsWith("{")) {
            return readTree(YAML, content, null);
        }
        try {
            return JSON.readTree(content);
        } catch (JacksonException jsonFailure) {
            return readTree(YAML, content, jsonFailure);
        }
    }

    private static JsonNode readTree(YAMLMapper mapper, String content, JacksonException earlier) {
        try {
            return mapper.readTree(content);
        } catch (JacksonException e) {
            throw new BratException("The OpenAPI document is neither JSON nor YAML"
                    + JacksonUtils.locationOf(earlier == null ? e : earlier));
        }
    }

    /** A node and the JSON pointer it was found at. */
    private record Located(JsonNode node, String location) {

        Located child(String key) {
            return new Located(node.get(key), location + "/" + escape(key));
        }

        Located element(int index) {
            return new Located(node.get(index), location + "/" + index);
        }

        boolean absent() {
            return node == null || node.isNull() || node.isMissingNode();
        }

        private static String escape(String key) {
            return key.replace("~", "~0").replace("/", "~1");
        }
    }

    /** One reading of one document: the root, against which every {@code $ref} is looked up. */
    private static final class Reading {

        private final JsonNode root;

        Reading(JsonNode root) {
            this.root = root;
        }

        OpenApiDocument document() {
            var top = new Located(root, "#");
            var info = optionalObject(top.child("info"));
            var title = info == null ? null : text(info.child("title"));
            var components = optionalObject(top.child("components"));
            return new OpenApiDocument(
                    title,
                    servers(top.child("servers")),
                    operations(top.child("paths")),
                    nullToEmpty(security(top.child("security"))),
                    securitySchemes(components == null ? null : components.child("securitySchemes")));
        }

        // --- servers ---

        private List<Server> servers(Located servers) {
            var result = new ArrayList<Server>();
            for (var entry : elements(servers)) {
                var server = object(entry);
                var url = required(server, "url");
                var defaults = new LinkedHashMap<String, String>();
                for (var variable : properties(optionalObject(server.child("variables")))) {
                    var value = text(object(variable.getValue()).child("default"));
                    if (value != null) {
                        defaults.put(variable.getKey(), value);
                    }
                }
                result.add(new Server(url, text(server.child("description")), Collections.unmodifiableMap(defaults)));
            }
            return List.copyOf(result);
        }

        // --- operations ---

        private List<Operation> operations(Located paths) {
            var result = new ArrayList<Operation>();
            for (var path : properties(optionalObject(paths))) {
                var item = object(resolve(path.getValue()));
                var shared = parameters(item.child("parameters"));
                for (var entry : properties(item)) {
                    if (METHODS.contains(entry.getKey())) {
                        result.add(operation(entry.getKey(), path.getKey(), object(entry.getValue()), shared));
                    }
                }
            }
            return List.copyOf(result);
        }

        private Operation operation(String method, String path, Located operation, List<Parameter> shared) {
            var parameters = new ArrayList<>(shared);
            for (var own : parameters(operation.child("parameters"))) {
                var replaced = false;
                for (var i = 0; i < parameters.size(); i++) {
                    var existing = parameters.get(i);
                    if (existing.name().equals(own.name()) && existing.in().equals(own.in())) {
                        parameters.set(i, own);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    parameters.add(own);
                }
            }
            var tags = new ArrayList<String>();
            for (var tag : elements(operation.child("tags"))) {
                tags.add(text(tag));
            }
            var responses = new ArrayList<String>();
            for (var response : properties(optionalObject(operation.child("responses")))) {
                responses.add(response.getKey());
            }
            return new Operation(
                    method.toUpperCase(Locale.ROOT),
                    path,
                    text(operation.child("operationId")),
                    text(operation.child("summary")),
                    List.copyOf(tags),
                    List.copyOf(parameters),
                    requestBody(operation.child("requestBody")),
                    List.copyOf(responses),
                    security(operation.child("security")));
        }

        private List<Parameter> parameters(Located parameters) {
            var result = new ArrayList<Parameter>();
            for (var entry : elements(parameters)) {
                var parameter = object(resolve(entry));
                var name = required(parameter, "name");
                var in = required(parameter, "in");
                var required = parameter.child("required");
                var isRequired = "path".equals(in)
                        || (!required.absent()
                                && required.node().isBoolean()
                                && required.node().asBoolean());
                result.add(new Parameter(name, in, isRequired, example(parameter, true)));
            }
            return List.copyOf(result);
        }

        private List<MediaType> requestBody(Located requestBody) {
            if (requestBody.absent()) {
                return List.of();
            }
            var body = object(resolve(requestBody));
            var result = new ArrayList<MediaType>();
            for (var entry : properties(optionalObject(body.child("content")))) {
                var media = object(entry.getValue());
                var schema = schema(media);
                var properties = new ArrayList<String>();
                if (schema != null) {
                    for (var property : properties(optionalObject(schema.child("properties")))) {
                        properties.add(property.getKey());
                    }
                }
                result.add(new MediaType(entry.getKey(), example(media, false), List.copyOf(properties)));
            }
            return List.copyOf(result);
        }

        /**
         * The first of {@code example}, the first {@code examples} entry's {@code value}, the schema's
         * {@code example} and, if {@code withDefault}, the schema's {@code default}.
         */
        private JsonNode example(Located owner, boolean withDefault) {
            var example = owner.child("example");
            if (!example.absent()) {
                return example.node();
            }
            var examples = properties(optionalObject(owner.child("examples")));
            if (!examples.isEmpty()) {
                var value = object(resolve(examples.getFirst().getValue())).child("value");
                if (!value.absent()) {
                    return value.node();
                }
            }
            var schema = schema(owner);
            if (schema == null) {
                return null;
            }
            var schemaExample = schema.child("example");
            if (!schemaExample.absent()) {
                return schemaExample.node();
            }
            var schemaDefault = schema.child("default");
            return withDefault && !schemaDefault.absent() ? schemaDefault.node() : null;
        }

        private Located schema(Located owner) {
            var schema = owner.child("schema");
            return schema.absent() ? null : object(resolve(schema));
        }

        // --- security ---

        private List<Map<String, List<String>>> security(Located security) {
            if (security.absent()) {
                return null;
            }
            var result = new ArrayList<Map<String, List<String>>>();
            for (var entry : elements(security)) {
                var requirement = new LinkedHashMap<String, List<String>>();
                for (var scheme : properties(object(entry))) {
                    var scopes = new ArrayList<String>();
                    for (var scope : elements(scheme.getValue())) {
                        scopes.add(text(scope));
                    }
                    requirement.put(scheme.getKey(), List.copyOf(scopes));
                }
                result.add(Collections.unmodifiableMap(requirement));
            }
            return List.copyOf(result);
        }

        private Map<String, SecurityScheme> securitySchemes(Located schemes) {
            var result = new LinkedHashMap<String, SecurityScheme>();
            if (schemes == null) {
                return Collections.unmodifiableMap(result);
            }
            for (var entry : properties(optionalObject(schemes))) {
                var scheme = object(resolve(entry.getValue()));
                var type = required(scheme, "type").toLowerCase(Locale.ROOT);
                result.put(
                        entry.getKey(),
                        switch (type) {
                            case "http" ->
                                new SecurityScheme(type, lowerCase(text(scheme.child("scheme"))), null, null, null);
                            case "apikey" ->
                                new SecurityScheme(
                                        type, null, text(scheme.child("in")), text(scheme.child("name")), null);
                            case "oauth2" ->
                                new SecurityScheme(type, null, null, null, clientCredentialsTokenUrl(scheme));
                            default -> new SecurityScheme(type, null, null, null, null);
                        });
            }
            return Collections.unmodifiableMap(result);
        }

        private String clientCredentialsTokenUrl(Located scheme) {
            var flows = optionalObject(scheme.child("flows"));
            var flow = flows == null ? null : optionalObject(flows.child("clientCredentials"));
            return flow == null ? null : text(flow.child("tokenUrl"));
        }

        // --- references and shapes ---

        /** Follows {@code $ref}s until a node without one. */
        private Located resolve(Located located) {
            var current = located;
            var seen = new HashSet<String>();
            while (current.node() != null
                    && current.node().isObject()
                    && current.node().has(REF)) {
                var refNode = current.child(REF);
                var ref = text(refNode);
                if (ref == null || !ref.startsWith("#/")) {
                    throw new BratException("The reference '" + ref + "' at " + current.location()
                            + " is not read: only references within the document (#/...) are");
                }
                if (!seen.add(ref)) {
                    throw new BratException("The reference '" + ref + "' leads back to itself");
                }
                var target = target(ref);
                if (target.isMissingNode()) {
                    throw new BratException(
                            "The reference '" + ref + "' at " + current.location() + " points at nothing");
                }
                current = new Located(target, ref);
            }
            return current;
        }

        /** The node a {@code #/…} reference names, its pointer percent-decoded as a URI fragment is. */
        private JsonNode target(String ref) {
            try {
                var pointer = URLDecoder.decode(ref.substring(1).replace("+", "%2B"), StandardCharsets.UTF_8);
                return root.at(pointer);
            } catch (IllegalArgumentException e) {
                return MissingNode.getInstance();
            }
        }

        private static Located object(Located located) {
            if (located.absent() || !located.node().isObject()) {
                throw new BratException("Expected a mapping at " + located.location());
            }
            return located;
        }

        private static Located optionalObject(Located located) {
            return located.absent() ? null : object(located);
        }

        private static List<Located> elements(Located list) {
            if (list.absent()) {
                return List.of();
            }
            if (!list.node().isArray()) {
                throw new BratException("Expected a list at " + list.location());
            }
            var result = new ArrayList<Located>();
            for (var i = 0; i < list.node().size(); i++) {
                result.add(list.element(i));
            }
            return result;
        }

        private static List<Map.Entry<String, Located>> properties(Located object) {
            if (object == null) {
                return List.of();
            }
            var result = new ArrayList<Map.Entry<String, Located>>();
            for (var entry : object.node().properties()) {
                result.add(Map.entry(entry.getKey(), object.child(entry.getKey())));
            }
            return result;
        }

        private static String text(Located located) {
            if (located.absent()) {
                return null;
            }
            if (located.node().isObject() || located.node().isArray()) {
                throw new BratException("Expected text at " + located.location());
            }
            return located.node().asString();
        }

        private static String required(Located object, String key) {
            var value = text(object.child(key));
            if (StringUtils.isBlank(value)) {
                throw new BratException("The entry at " + object.location() + " has no '" + key + "'");
            }
            return value;
        }

        private static String lowerCase(String text) {
            return text == null ? null : text.toLowerCase(Locale.ROOT);
        }

        private static <T> List<T> nullToEmpty(List<T> list) {
            return list == null ? List.of() : list;
        }
    }
}
