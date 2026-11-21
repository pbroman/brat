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
import dev.pbroman.brat.core.loader.YamlComposer;
import dev.pbroman.brat.core.loader.YamlLimits;
import dev.pbroman.brat.core.util.JacksonUtils;
import dev.pbroman.brat.openapi.OpenApiDocument.Field;
import dev.pbroman.brat.openapi.OpenApiDocument.MediaType;
import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.Parameter;
import dev.pbroman.brat.openapi.OpenApiDocument.Schema;
import dev.pbroman.brat.openapi.OpenApiDocument.SecurityScheme;
import dev.pbroman.brat.openapi.OpenApiDocument.Server;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.NullNode;

/**
 * Parses the content of an OpenAPI 3.0, 3.1 or 3.2 document into an {@link OpenApiDocument}, following
 * the {@code $ref}s within the document that the importer needs.
 * <p>
 * It holds no state, so it may be called from several threads at once.
 */
final class OpenApiLoader {

    /** The largest document read, JSON or YAML, in megabytes of text. */
    static final int MAX_MEGABYTES = 64;

    /** How a YAML document is read: within {@link #MAX_MEGABYTES} and 50 aliases. */
    private static final YamlLimits YAML_LIMITS = new YamlLimits("The OpenAPI document", MAX_MEGABYTES, 50);

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build();

    private static final Set<String> METHODS =
            Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace", "query");

    private static final Pattern SUPPORTED_VERSION = Pattern.compile("3\\.[012](\\..*)?");

    private static final String REF = "$ref";

    /** A schema's properties and items are read for three levels below the schema it started from. */
    private static final int MAX_LEVEL = 3;

    private static final List<String> PARAMETER_EXAMPLE_KEYS = List.of("dataValue", "value");

    private static final String SERIALIZED_VALUE = "serializedValue";

    private static final List<String> MEDIA_EXAMPLE_KEYS = List.of("dataValue", "value", SERIALIZED_VALUE);

    private OpenApiLoader() {
        // static helpers only
    }

    /**
     * Parses {@code content}, JSON or YAML, recognised by the content alone: content whose first
     * non-blank character is an opening brace is read as JSON, and as YAML only if it is not JSON
     * (flow-style YAML); anything else as YAML. In YAML, anchors, aliases and {@code <<:} merge keys
     * are resolved and at most 50 aliases to a mapping or list are allowed. In either, a key written
     * twice in one mapping is refused.
     * <p>
     * <strong>What is read</strong> is only what {@link OpenApiDocument} holds; every other key is
     * ignored, extensions ({@code x-…}) included. Of a path item, the keys {@code get}, {@code put},
     * {@code post}, {@code delete}, {@code options}, {@code head}, {@code patch}, {@code trace} and
     * {@code query} are operations, and {@code parameters} and {@code servers} apply to each of them.
     * Its {@code additionalOperations}, and the document's {@code webhooks}, are not read beyond their
     * names, which {@link OpenApiDocument#notImported()} lists.
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
     *           <li>if it is longer than {@link #MAX_MEGABYTES} megabytes of text — 2<sup>20</sup> code
     *               points each, as YAML counts them — before any of it is parsed: "The OpenAPI document
     *               is N MB; at most 64 MB is read", N rounded up;</li>
     *           <li>if it is read as YAML and is not valid YAML: "The OpenAPI document is not valid
     *               YAML: " and the parser's reason, with its line and column;</li>
     *           <li>if it starts with a brace and is neither valid JSON nor valid YAML: "The OpenAPI
     *               document is not valid JSON: " and the JSON parser's reason, then " at line L, column
     *               C" — the reason never quoting the content;</li>
     *           <li>if it uses more than 50 aliases to a mapping or a list: "The OpenAPI document uses
     *               more than the 50 permitted aliases";</li>
     *           <li>if a mapping holds the same key twice, naming the key and its position — for JSON,
     *               within the "not valid JSON" message above;</li>
     *           <li>if its top is not a mapping, or has neither {@code openapi} nor {@code swagger}:
     *               "not an OpenAPI document";</li>
     *           <li>if it has {@code swagger}: it names Swagger 2 and says to convert it to OpenAPI 3
     *               first;</li>
     *           <li>if {@code openapi} is not text starting {@code 3.0}, {@code 3.1} or {@code 3.2}
     *               followed by the end or a {@code .}, naming the version found;</li>
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
                    "OpenAPI " + version.asString() + " is not supported; only OpenAPI 3.0, 3.1 and 3.2 are read");
        }
        return new Reading(root).document();
    }

    /**
     * Checks the size, then parses: JSON first for content starting with an opening brace, falling back
     * to YAML for a flow-style YAML document, YAML for anything else — reporting each failure as
     * {@link #load(String)} documents.
     */
    private static JsonNode parse(String content) {
        var megabyte = 1024 * 1024;
        // A code point is one or two chars, so only text longer than the limit in chars can exceed it.
        var codePoints = content.length() > MAX_MEGABYTES * megabyte
                ? content.codePointCount(0, content.length())
                : content.length();
        if (codePoints > MAX_MEGABYTES * megabyte) {
            var size = (codePoints + megabyte - 1) / megabyte;
            throw new BratException(
                    "The OpenAPI document is " + size + " MB; at most " + MAX_MEGABYTES + " MB is read");
        }
        if (!content.stripLeading().startsWith("{")) {
            return yaml(content);
        }
        try {
            return JSON.readTree(content);
        } catch (JacksonException jsonFailure) {
            try {
                return yaml(content);
            } catch (BratException yamlFailure) {
                throw new BratException("The OpenAPI document is not valid JSON: " + jsonFailure.getOriginalMessage()
                        + JacksonUtils.locationOf(jsonFailure));
            }
        }
    }

    /** Composes YAML with its aliases and merge keys resolved, within {@link #YAML_LIMITS}. */
    private static JsonNode yaml(String content) {
        var tree = YamlComposer.tree(content, YAML_LIMITS);
        return tree == null ? NullNode.getInstance() : JSON.valueToTree(tree);
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
                    securitySchemes(components == null ? null : components.child("securitySchemes")),
                    notImported(top));
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
                var pathServers = servers(item.child("servers"));
                for (var entry : properties(item)) {
                    if (METHODS.contains(entry.getKey())) {
                        result.add(operation(
                                entry.getKey(), path.getKey(), object(entry.getValue()), shared, pathServers));
                    }
                }
            }
            return List.copyOf(result);
        }

        private Operation operation(
                String method, String path, Located operation, List<Parameter> shared, List<Server> pathServers) {
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
                    security(operation.child("security")),
                    operationServers(operation, pathServers));
        }

        /** The operation's own servers, else its path item's. */
        private List<Server> operationServers(Located operation, List<Server> pathServers) {
            var own = servers(operation.child("servers"));
            return own.isEmpty() ? pathServers : own;
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
                result.add(new Parameter(name, in, isRequired, example(parameter, true), schemaOf(parameter)));
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
                result.add(new MediaType(entry.getKey(), example(media, false), isSerialized(media), schemaOf(media)));
            }
            return List.copyOf(result);
        }

        /**
         * The example {@link Parameter#example()} and {@link MediaType#example()} describe; a media type's
         * may also come from {@code serializedValue}, a parameter's from its schema's {@code default}.
         */
        private JsonNode example(Located owner, boolean forParameter) {
            var example = owner.child("example");
            if (!example.absent()) {
                return example.node();
            }
            var entry = firstExamplesEntry(owner);
            var key = entry == null
                    ? null
                    : firstKeyHeld(entry, forParameter ? PARAMETER_EXAMPLE_KEYS : MEDIA_EXAMPLE_KEYS);
            if (key != null) {
                return entry.child(key).node();
            }
            var schema = schema(owner);
            if (schema == null) {
                return null;
            }
            var schemaExample = schemaExample(schema);
            if (schemaExample != null) {
                return schemaExample;
            }
            var schemaDefault = schema.child("default");
            return forParameter && !schemaDefault.absent() ? schemaDefault.node() : null;
        }

        /** Whether {@link #example} took a media type's example from its first entry's {@code serializedValue}. */
        private boolean isSerialized(Located media) {
            if (!media.child("example").absent()) {
                return false;
            }
            var entry = firstExamplesEntry(media);
            return entry != null && SERIALIZED_VALUE.equals(firstKeyHeld(entry, MEDIA_EXAMPLE_KEYS));
        }

        /** The first of {@code keys} the entry holds a value for, or {@code null}: the one rule both use. */
        private static String firstKeyHeld(Located entry, List<String> keys) {
            for (var key : keys) {
                if (!entry.child(key).absent()) {
                    return key;
                }
            }
            return null;
        }

        /** The first entry of the owner's {@code examples} mapping, its reference followed, or {@code null}. */
        private Located firstExamplesEntry(Located owner) {
            var examples = properties(optionalObject(owner.child("examples")));
            return examples.isEmpty()
                    ? null
                    : object(resolve(examples.getFirst().getValue()));
        }

        /** A schema's {@code example}, else the first of its {@code examples} list, else {@code null}. */
        private static JsonNode schemaExample(Located schema) {
            var example = schema.child("example");
            if (!example.absent()) {
                return example.node();
            }
            var examples = schema.child("examples");
            if (!examples.absent()
                    && examples.node().isArray()
                    && !examples.node().isEmpty()) {
                return examples.node().get(0);
            }
            return null;
        }

        /** The owner's {@code schema}, read as {@link Schema} describes, or {@code null} if it has none. */
        private Schema schemaOf(Located owner) {
            var schema = owner.child("schema");
            return schema.absent() ? null : readSchema(schema, 0);
        }

        /** Reads one schema at a nesting level: properties and items only below {@link #MAX_LEVEL}. */
        private Schema readSchema(Located located, int level) {
            if (!isMapping(located)) {
                return new Schema(null, null, null, List.of(), List.of(), null);
            }
            var parts = new ArrayList<Located>();
            collectParts(object(resolve(located)), parts, new HashSet<>());
            String type = null;
            String format = null;
            JsonNode example = null;
            List<String> enumValues = List.of();
            Located items = null;
            var properties = new LinkedHashMap<String, Located>();
            var required = new HashSet<String>();
            for (var part : parts) {
                type = firstNonNull(typeOf(part), type);
                var partFormat = part.child("format");
                format = !partFormat.absent() && partFormat.node().isString()
                        ? partFormat.node().asString()
                        : format;
                example = firstNonNull(schemaExample(part), example);
                var partEnum = enumOf(part);
                enumValues = partEnum.isEmpty() ? enumValues : partEnum;
                items = isMapping(part.child("items")) ? part.child("items") : items;
                if (isMapping(part.child("properties"))) {
                    for (var property : properties(part.child("properties"))) {
                        properties.put(property.getKey(), property.getValue());
                    }
                }
                for (var name : listOrEmpty(part.child("required"))) {
                    if (name.node().isString()) {
                        required.add(name.node().asString());
                    }
                }
            }
            if (type == null) {
                type = !properties.isEmpty() ? "object" : items != null ? "array" : null;
            }
            if (level >= MAX_LEVEL) {
                return new Schema(type, format, example, enumValues, List.of(), null);
            }
            var fields = new ArrayList<Field>();
            for (var property : properties.entrySet()) {
                fields.add(new Field(
                        property.getKey(),
                        required.contains(property.getKey()),
                        readSchema(property.getValue(), level + 1)));
            }
            return new Schema(
                    type,
                    format,
                    example,
                    enumValues,
                    List.copyOf(fields),
                    items == null ? null : readSchema(items, level + 1));
        }

        /**
         * Adds the parts a schema is merged from, in order: its {@code allOf} members, its first
         * {@code oneOf} or {@code anyOf} alternative, then itself. A member already being read is
         * skipped, so a cycle of {@code allOf}s ends.
         */
        private void collectParts(Located schema, List<Located> parts, Set<String> reading) {
            if (!reading.add(schema.location())) {
                return;
            }
            for (var member : listOrEmpty(schema.child("allOf"))) {
                if (isMapping(member)) {
                    collectParts(object(resolve(member)), parts, reading);
                }
            }
            for (var keyword : List.of("oneOf", "anyOf")) {
                var alternatives = listOrEmpty(schema.child(keyword));
                if (!alternatives.isEmpty()) {
                    if (isMapping(alternatives.getFirst())) {
                        collectParts(object(resolve(alternatives.getFirst())), parts, reading);
                    }
                    break;
                }
            }
            parts.add(schema);
            reading.remove(schema.location());
        }

        /** A schema's own {@code type}: the first that is not {@code null} of a list, or {@code null}. */
        private static String typeOf(Located schema) {
            var type = schema.child("type");
            if (type.absent()) {
                return null;
            }
            if (type.node().isString()) {
                return type.node().asString();
            }
            if (!type.node().isArray()) {
                return null;
            }
            for (var entry : type.node()) {
                if (entry.isString() && !"null".equals(entry.asString())) {
                    return entry.asString();
                }
            }
            return null;
        }

        /** A schema's own {@code enum} values as text, a {@code null} or a structure left out. */
        private static List<String> enumOf(Located schema) {
            var result = new ArrayList<String>();
            for (var value : listOrEmpty(schema.child("enum"))) {
                if (!value.absent() && value.node().isValueNode()) {
                    result.add(value.node().asString());
                }
            }
            return List.copyOf(result);
        }

        /** Whether the node is present and a mapping. */
        private static boolean isMapping(Located located) {
            return !located.absent() && located.node().isObject();
        }

        /** The elements of a list, or none for anything that is not one: a schema's mistakes are ignored. */
        private static List<Located> listOrEmpty(Located list) {
            return list.absent() || !list.node().isArray() ? List.of() : elements(list);
        }

        private static <T> T firstNonNull(T preferred, T fallback) {
            return preferred != null ? preferred : fallback;
        }

        /** What {@link OpenApiDocument#notImported()} describes. */
        private List<String> notImported(Located top) {
            var result = new ArrayList<String>();
            for (var entry : properties(top)) {
                if ("webhooks".equals(entry.getKey())) {
                    var names = properties(optionalObject(entry.getValue()));
                    if (!names.isEmpty()) {
                        result.add("webhooks: " + joinKeys(names));
                    }
                } else if ("paths".equals(entry.getKey())) {
                    for (var path : properties(optionalObject(entry.getValue()))) {
                        var item = object(resolve(path.getValue()));
                        var extra = properties(optionalObject(item.child("additionalOperations")));
                        if (!extra.isEmpty()) {
                            result.add("additionalOperations on " + path.getKey() + ": " + joinKeys(extra));
                        }
                    }
                }
            }
            return List.copyOf(result);
        }

        private static String joinKeys(List<Map.Entry<String, Located>> entries) {
            return String.join(", ", entries.stream().map(Map.Entry::getKey).toList());
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
