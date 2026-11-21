package dev.pbroman.brat.openapi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.pbroman.brat.core.secrets.SecretsUtils;
import dev.pbroman.brat.openapi.OpenApiDocument.MediaType;
import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.Parameter;
import dev.pbroman.brat.openapi.OpenApiDocument.Server;
import dev.pbroman.brat.openapi.Scaffold.Note;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the text of a scaffold for an {@link OpenApiDocument}: the suite file, its body files and its
 * environment files, with comments, and the notes on them.
 * <p>
 * It holds no state, so it may be called from several threads at once.
 */
final class SuiteEmitter {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String FALLBACK_NAME = "imported";
    private static final String SUITE_EXTENSION = ".brat.yaml";
    private static final String BODIES = "bodies";
    private static final String ENV_FILE = "/env.yaml";
    private static final String LOCAL = "local";
    private static final String PLACEHOLDER = "${";
    private static final String SECRETS_TOKEN = "${secrets.";
    private static final ImportAuth.Block NONE_BLOCK = new ImportAuth.Block(ImportAuth.NONE, Map.of());

    private static final Set<String> IGNORED_HEADERS = Set.of("accept", "content-type", "authorization");
    private static final String FORM = "application/x-www-form-urlencoded";

    private static final Pattern PATH_PARAMETER = Pattern.compile("\\{([^}/]+)}");
    private static final Pattern SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://.*");
    private static final Pattern VARIABLE = Pattern.compile("\\{([^}]*)}");

    /** The column a comment after a value starts at, where the value leaves room. */
    private static final int COMMENT_COLUMN = 43;

    private SuiteEmitter() {
        // static helpers only
    }

    /**
     * Emits the scaffold for {@code document}, mapping it as {@link OpenApiImporter#scaffold} describes.
     *
     * @param document the document; never {@code null}
     * @param sourceName what the header comment calls the document; may be {@code null}
     * @param options the caller's choices; never {@code null}
     * @return the scaffold
     */
    static Scaffold emit(OpenApiDocument document, String sourceName, ImportOptions options) {
        return new Emission(document, sourceName, options).scaffold();
    }

    /** A constant: the first operation's value for it, and every request's example. */
    private record Constant(String name, String in, JsonNode example, List<Use> uses) {}

    /** One request's example for a constant. */
    private record Use(String request, String example) {}

    /** The requests under one parent: the top of the suite, or one sub-suite. */
    private record Group(String name, List<Operation> operations) {}

    /** One emission, holding what it has written so far. */
    private static final class Emission {

        private final OpenApiDocument document;
        private final String sourceName;
        private final ImportOptions options;

        private final Map<Operation, String> requestNames = new IdentityHashMap<>();
        private final Map<String, Constant> constants = new LinkedHashMap<>();
        private final Set<String> bodyFiles = new HashSet<>();
        private final Map<String, String> bodies = new LinkedHashMap<>();
        private final List<Note> suiteNotes = new ArrayList<>();
        private final List<Note> envNotes = new ArrayList<>();
        private final Set<String> notedSecrets = new HashSet<>();
        private final List<String> tokenRequestNames = new ArrayList<>();
        private ImportAuth.Plan auth;
        private String suiteFile;

        Emission(OpenApiDocument document, String sourceName, ImportOptions options) {
            this.document = document;
            this.sourceName = sourceName;
            this.options = options;
        }

        Scaffold scaffold() {
            var directory = orFallback(ImportNames.fileName(document.title()));
            suiteFile = directory + SUITE_EXTENSION;
            var top = new ArrayList<Operation>();
            var tagged = new LinkedHashMap<String, List<Operation>>();
            for (var operation : document.operations()) {
                var tag = operation.tags().isEmpty() ? null : operation.tags().getFirst();
                if (ImportNames.nodeName(tag).isEmpty()) {
                    top.add(operation);
                } else {
                    tagged.computeIfAbsent(tag, key -> new ArrayList<>()).add(operation);
                }
            }
            auth = ImportAuth.declaresSecurity(document) ? ImportAuth.plan(document) : undeclared();
            var subSuites = nameGroups(top, tagged);
            collectConstants();

            var suite = suiteText(top, subSuites);
            var files = new LinkedHashMap<String, String>();
            files.put(suiteFile, suite);
            files.putAll(bodies);
            var environments = environments(files);

            var notes = new ArrayList<>(suiteNotes);
            notes.addAll(envNotes);
            return new Scaffold(
                    directory,
                    suiteFile,
                    files,
                    environments,
                    document.operations().size(),
                    subSuites.size(),
                    auth.tokenRequests().size(),
                    notes);
        }

        private ImportAuth.Plan undeclared() {
            var requests = new IdentityHashMap<Operation, ImportAuth.Auth>();
            for (var operation : document.operations()) {
                requests.put(operation, ImportAuth.Auth.UNDECLARED);
            }
            return new ImportAuth.Plan(ImportAuth.Auth.UNDECLARED, requests, List.of());
        }

        // --- names ---

        /** Names every request and sub-suite, and returns the sub-suites with their final names. */
        private List<Group> nameGroups(List<Operation> top, Map<String, List<Operation>> tagged) {
            var topNames = new ArrayList<String>();
            for (var tokenRequest : auth.tokenRequests()) {
                topNames.add(tokenRequest.name());
            }
            for (var operation : top) {
                topNames.add(ImportNames.requestName(operation));
            }
            for (var tag : tagged.keySet()) {
                topNames.add(ImportNames.nodeName(tag));
            }
            var unique = ImportNames.unique(topNames, " ", Set.of());
            var tokenCount = auth.tokenRequests().size();
            tokenRequestNames.addAll(unique.subList(0, tokenCount));
            var index = tokenCount;
            for (var operation : top) {
                requestNames.put(operation, unique.get(index++));
            }
            var groups = new ArrayList<Group>();
            for (var operations : tagged.values()) {
                var names = new ArrayList<String>();
                for (var operation : operations) {
                    names.add(ImportNames.requestName(operation));
                }
                var uniqueNames = ImportNames.unique(names, " ", Set.of());
                for (var i = 0; i < operations.size(); i++) {
                    requestNames.put(operations.get(i), uniqueNames.get(i));
                }
                groups.add(new Group(unique.get(index++), operations));
            }
            return groups;
        }

        // --- constants ---

        private void collectConstants() {
            for (var operation : document.operations()) {
                var request = requestNames.get(operation);
                for (var parameter : sentParameters(operation)) {
                    var constant = constants.computeIfAbsent(
                            parameter.name(),
                            name -> new Constant(name, parameter.in(), parameter.example(), new ArrayList<>()));
                    constant.uses().add(new Use(request, exampleText(parameter.example())));
                }
            }
        }

        /**
         * The parameters an operation sends from a constant: its declared path, required query and
         * required header parameters in order, then any path template name it does not declare.
         */
        private static List<Parameter> sentParameters(Operation operation) {
            var result = new ArrayList<Parameter>();
            var declaredPath = new HashSet<String>();
            for (var parameter : operation.parameters()) {
                if (isSent(parameter)) {
                    result.add(parameter);
                }
                if ("path".equals(parameter.in())) {
                    declaredPath.add(parameter.name());
                }
            }
            var matcher = PATH_PARAMETER.matcher(operation.path());
            while (matcher.find()) {
                if (declaredPath.add(matcher.group(1))) {
                    result.add(new Parameter(matcher.group(1), "path", true, null, null));
                }
            }
            return result;
        }

        private static boolean isSent(Parameter parameter) {
            return switch (parameter.in()) {
                case "path" -> true;
                case "query" -> parameter.required();
                case "header" -> parameter.required() && !isIgnoredHeader(parameter);
                default -> false;
            };
        }

        private static boolean isIgnoredHeader(Parameter parameter) {
            return IGNORED_HEADERS.contains(parameter.name().toLowerCase(Locale.ROOT));
        }

        /** An example as the text of a single value, or {@code null} for none, a list or a mapping. */
        private static String exampleText(JsonNode example) {
            if (example == null || example.isContainer()) {
                return null;
            }
            return example.asString();
        }

        private void writeConstants(Lines lines) {
            if (constants.isEmpty()) {
                return;
            }
            lines.add("constants:");
            for (var constant : constants.values()) {
                var key = "  " + YamlText.scalar(constant.name()) + ": ";
                var example = constant.example();
                var text = exampleText(example);
                String reason = null;
                if (example == null) {
                    reason = "no example in the spec";
                } else if (example.isContainer()) {
                    reason = "the spec's example is not a single value";
                } else if (text.contains(PLACEHOLDER)) {
                    reason = "the spec's example holds ${, which would be read as a placeholder";
                }
                if (reason != null) {
                    lines.add(key + "\"\"", reason + ": fill in");
                    note(suiteNotes, "constants." + constant.name() + ": " + reason + "; fill in a value");
                } else {
                    var value = "header".equals(constant.in()) ? text : percentEncoded(text);
                    var comment = value.equals(text)
                            ? "example from the spec"
                            : "the spec's example, encoded: " + YamlText.commentText(text);
                    lines.add(key + YamlText.scalar(value), comment);
                }
                var differing = differingUses(constant, text);
                if (!differing.isEmpty()) {
                    var named = new ArrayList<String>();
                    for (var use : differing) {
                        named.add("\"" + use.request() + "\" uses " + use.example());
                    }
                    var others = String.join(", ", named);
                    lines.comment("first request's example; " + YamlText.commentText(others));
                    note(
                            suiteNotes,
                            "constants." + constant.name() + " serves requests whose examples differ (" + others
                                    + "); it holds the first request's");
                }
            }
            lines.blank();
        }

        private static List<Use> differingUses(Constant constant, String first) {
            var result = new ArrayList<Use>();
            for (var use : constant.uses().subList(1, constant.uses().size())) {
                if (use.example() != null && !use.example().equals(first)) {
                    result.add(use);
                }
            }
            return result;
        }

        // --- the suite ---

        private String suiteText(List<Operation> top, List<Group> subSuites) {
            var lines = new Lines();
            lines.add("# " + header());
            lines.add("name: " + YamlText.scalar(orFallback(ImportNames.nodeName(document.title()))));
            lines.blank();
            writeConstants(lines);
            writeSuiteAuth(lines);
            if (!top.isEmpty() || !auth.tokenRequests().isEmpty()) {
                lines.add("requests:");
                for (var i = 0; i < auth.tokenRequests().size(); i++) {
                    writeTokenRequest(lines, auth.tokenRequests().get(i), tokenRequestNames.get(i));
                    lines.blank();
                }
                writeRequests(lines, top, "");
            }
            if (!subSuites.isEmpty()) {
                lines.add("subSuites:");
                for (var group : subSuites) {
                    lines.add("  - name: " + YamlText.scalar(group.name()));
                    lines.add("    requests:");
                    writeRequests(lines, group.operations(), "    ");
                }
            }
            return lines.text();
        }

        private String header() {
            var from = sourceName == null ? "an OpenAPI document" : YamlText.commentText(sourceName);
            var title = document.title() == null ? "" : " (" + YamlText.commentText(document.title()) + ")";
            return "Imported from " + from + title + ". A starting point: edit freely.";
        }

        private void writeRequests(Lines lines, List<Operation> operations, String indent) {
            for (var operation : operations) {
                writeRequest(lines, operation, indent);
                lines.blank();
            }
        }

        private void writeRequest(Lines lines, Operation operation, String indent) {
            var name = requestNames.get(operation);
            var in = indent + "    ";
            var own = auth.requests().get(operation);
            lines.add(indent + "  - name: " + YamlText.scalar(name));
            if (carriesOwnAuth(own)) {
                var block = own.block() == null ? NONE_BLOCK : own.block();
                var source = "security: []".equals(own.source())
                        ? "security: [] in the spec"
                        : "the spec's security: " + own.source();
                writeAuth(lines, in, block, source, own.todo(), "\"" + name + "\"");
            }
            lines.add(in + "requestDefinition:");
            var url = url(operation);
            if (own.queryName() != null) {
                url += (url.contains("?") ? "&" : "?") + percentEncoded(own.queryName()) + "=${secrets."
                        + own.querySecret() + "}";
            }
            lines.add(in + "  url: " + YamlText.scalar(url), urlComment(operation, name));
            if (own.queryName() != null) {
                noteSecret(lines, own.querySecret());
                if (own.todo() != null && !carriesOwnAuth(own)) {
                    lines.comment(YamlText.commentText(own.todo()));
                    note(suiteNotes, "\"" + name + "\": " + own.todo());
                }
            }
            if (!"GET".equals(operation.method())) {
                lines.add(in + "  method: " + operation.method());
            }
            var media = chosenMedia(operation.requestBody());
            var headers = new LinkedHashMap<String, String>();
            for (var parameter : operation.parameters()) {
                if ("header".equals(parameter.in()) && isSent(parameter)) {
                    headers.put(parameter.name(), "${constants." + parameter.name() + "}");
                }
            }
            if (media != null && !isForm(media.type())) {
                headers.put("Content-Type", media.type());
            }
            if (!headers.isEmpty()) {
                lines.add(in + "  headers:");
                for (var header : headers.entrySet()) {
                    lines.add(
                            in + "    " + YamlText.scalar(header.getKey()) + ": " + YamlText.scalar(header.getValue()));
                }
            }
            if (media != null) {
                lines.add(in + "  body:");
                writeBody(lines, media, name, in + "    ");
            }
            var status = successStatus(operation.responseCodes());
            if (status.isPresent()) {
                lines.add(in + "responseActions:");
                lines.add(in + "  assertions:");
                lines.add(in + "    - a: \"${response.statusCode}\"");
                lines.add(in + "      func: isEqualTo");
                lines.add(in + "      b: " + status.getAsInt());
            } else {
                lines.add(in + "# the spec documents no success status: add an assertion");
                note(suiteNotes, "\"" + name + "\": the spec documents no success status; add an assertion");
            }
        }

        // --- auth ---

        /**
         * Whether a request writes its own {@code auth:}: where its block differs from the suite's, except
         * a plain {@code none} under a suite declaring no auth, which would cancel nothing.
         */
        private boolean carriesOwnAuth(ImportAuth.Auth own) {
            var suiteBlock = auth.suite().block();
            if (Objects.equals(own.block(), suiteBlock)) {
                return false;
            }
            // A suite without a block means own's is not null: the two differ.
            return suiteBlock != null || !ImportAuth.NONE.equals(own.block().type()) || own.todo() != null;
        }

        private void writeSuiteAuth(Lines lines) {
            var suite = auth.suite();
            if (suite.block() == null) {
                return;
            }
            writeAuth(
                    lines,
                    "",
                    suite.block(),
                    "the spec's default security: " + suite.source(),
                    suite.todo(),
                    "the suite");
            lines.blank();
        }

        /** Writes an {@code auth:} block at {@code in}, with its comment, its todo and its secrets' notes. */
        private void writeAuth(
                Lines lines, String in, ImportAuth.Block block, String source, String todo, String owner) {
            lines.add(in + "auth:", YamlText.commentText(source));
            lines.add(in + "  type: " + block.type());
            if (todo != null) {
                lines.comment(YamlText.commentText(todo));
                note(suiteNotes, owner + ": " + todo);
            }
            if (block.args().isEmpty()) {
                return;
            }
            lines.add(in + "  args:");
            for (var arg : block.args().entrySet()) {
                var key = in + "    " + YamlText.scalar(arg.getKey()) + ": " + YamlText.scalar(arg.getValue());
                var secret = secretIn(arg.getValue());
                var setBy = setterOf(arg.getValue());
                if (secret != null && notedSecrets.add(secret)) {
                    lines.add(key, secretComment(secret));
                    note(suiteNotes, secretMessage(secret), secret);
                } else if (setBy != null) {
                    lines.add(
                            key,
                            "set by \"" + YamlText.commentText(setBy) + "\" " + (in.isEmpty() ? "below" : "above"));
                } else {
                    lines.add(key);
                }
            }
        }

        private void writeTokenRequest(Lines lines, ImportAuth.TokenRequest request, String name) {
            lines.add("  - name: " + YamlText.scalar(name));
            lines.add("    phase: setup", "runs first; if it fails, nothing else runs");
            lines.add(
                    "    skipCondition:",
                    "skipped when a token is handed over (vars." + YamlText.commentText(request.accessTokenVar())
                            + ")");
            lines.add("      a: " + YamlText.scalar("${vars." + request.accessTokenVar() + "}"));
            lines.add("      func: isNotBlank");
            lines.add("    auth:");
            lines.add("      type: none", "the token request must not send the token it fetches");
            lines.add("    requestDefinition:");
            lines.add("      url: " + YamlText.scalar("${env." + request.tokenUrlKey() + "}"));
            lines.add("      method: POST");
            lines.add("      body:");
            lines.add("        form:");
            lines.add("          grant_type: client_credentials");
            lines.add("          client_id: " + YamlText.scalar("${env." + request.clientIdKey() + "}"));
            var secret = request.clientSecretKey();
            var clientSecret = "          client_secret: " + YamlText.scalar("${secrets." + secret + "}");
            // A client secret's key is its scheme's alone, and its token request is where it is first written.
            notedSecrets.add(secret);
            lines.add(clientSecret, secretComment(secret));
            note(suiteNotes, secretMessage(secret), secret);
            if (!request.scope().isEmpty()) {
                lines.add("          scope: " + YamlText.scalar(request.scope()));
            }
            lines.add("    responseActions:");
            lines.add("      assertions:");
            lines.add("        - a: \"${response.statusCode}\"");
            lines.add("          func: isEqualTo");
            lines.add("          b: 200");
            lines.add("      setVars:");
            lines.add("        " + YamlText.scalar(request.accessTokenVar()) + ": \"${response.json.$.access_token}\"");
        }

        /** Notes a secret on the line just written, the first time it is written. */
        private void noteSecret(Lines lines, String secret) {
            if (notedSecrets.add(secret)) {
                lines.comment(secretComment(secret));
                note(suiteNotes, secretMessage(secret), secret);
            }
        }

        /** The secret key a value reads, if it is exactly {@code ${secrets.<key>}}. */
        private static String secretIn(String value) {
            return value.startsWith(SECRETS_TOKEN) && value.endsWith("}")
                    ? value.substring(SECRETS_TOKEN.length(), value.length() - 1)
                    : null;
        }

        /** The name of the token request setting a {@code ${vars.<key>}} value, if one does. */
        private String setterOf(String value) {
            for (var i = 0; i < auth.tokenRequests().size(); i++) {
                if (value.equals("${vars." + auth.tokenRequests().get(i).accessTokenVar() + "}")) {
                    return tokenRequestNames.get(i);
                }
            }
            return null;
        }

        private static String secretComment(String secret) {
            return SecretsUtils.envVarName(SecretsUtils.DEFAULT_ENV_PREFIX, secret) + ", or a secrets file";
        }

        private static String secretMessage(String secret) {
            return "provide the secret " + secret + ": set "
                    + SecretsUtils.envVarName(SecretsUtils.DEFAULT_ENV_PREFIX, secret)
                    + ", or put it in a secrets file";
        }

        private static String url(Operation operation) {
            var path = PATH_PARAMETER
                    .matcher(operation.path())
                    .replaceAll(match -> Matcher.quoteReplacement("${constants." + match.group(1) + "}"));
            var query = new ArrayList<String>();
            for (var parameter : operation.parameters()) {
                if ("query".equals(parameter.in()) && parameter.required()) {
                    query.add(percentEncoded(parameter.name()) + "=${constants." + parameter.name() + "}");
                }
            }
            return "${env.baseUrl}" + path + (query.isEmpty() ? "" : "?" + String.join("&", query));
        }

        private String urlComment(Operation operation, String request) {
            var optional = new ArrayList<String>();
            var parts = new ArrayList<String>();
            for (var parameter : operation.parameters()) {
                if (!parameter.required() && "query".equals(parameter.in())) {
                    optional.add(parameter.name());
                } else if (!parameter.required() && "header".equals(parameter.in()) && !isIgnoredHeader(parameter)) {
                    optional.add(parameter.name() + " (header)");
                } else if (parameter.required() && "cookie".equals(parameter.in())) {
                    parts.add("cookie " + parameter.name() + " is required and not sent");
                    note(
                            suiteNotes,
                            "\"" + request + "\": the spec requires the cookie " + parameter.name()
                                    + ", which the import does not send");
                }
            }
            if (!optional.isEmpty()) {
                parts.addFirst("optional: " + String.join(", ", optional));
            }
            return parts.isEmpty() ? null : YamlText.commentText(String.join("; ", parts));
        }

        // --- bodies ---

        private static MediaType chosenMedia(List<MediaType> media) {
            if (media.isEmpty()) {
                return null;
            }
            for (var type : media) {
                if (isJson(type.type())) {
                    return type;
                }
            }
            for (var type : media) {
                if (isForm(type.type())) {
                    return type;
                }
            }
            return media.getFirst();
        }

        private static boolean isJson(String type) {
            var bare = bareType(type);
            return "application/json".equals(bare) || bare.endsWith("+json");
        }

        private static boolean isForm(String type) {
            return FORM.equals(bareType(type));
        }

        /** A media type without its parameters, in lower case: {@code application/json}. */
        private static String bareType(String type) {
            return type.toLowerCase(Locale.ROOT).split(";", 2)[0].strip();
        }

        private void writeBody(Lines lines, MediaType media, String request, String in) {
            if (isJson(media.type())) {
                writeJsonBody(lines, media.example(), request, in);
            } else if (isForm(media.type())) {
                writeFormBody(lines, media, request, in);
            } else {
                var reason = "a " + media.type() + " body: write it here";
                lines.add(in + "raw: \"\"", YamlText.commentText(reason));
                note(suiteNotes, "\"" + request + "\": " + reason);
            }
        }

        private void writeJsonBody(Lines lines, JsonNode example, String request, String in) {
            if (example == null) {
                emptyBody(lines, request, in, "no example in the spec");
                return;
            }
            var json = example;
            if (example.isString()) {
                try {
                    json = JSON.readTree(example.asString());
                } catch (JacksonException notJson) {
                    json = null;
                }
            }
            if (json == null) {
                var text = example.asString();
                if (text.contains(PLACEHOLDER)) {
                    emptyBody(lines, request, in, "the spec's example holds ${, which would be read as a placeholder");
                    return;
                }
                lines.add(in + "raw: " + YamlText.scalar(text), "the spec's example is not JSON; written as it is");
                return;
            }
            var printed = printed(json, "");
            if (printed.contains(PLACEHOLDER)) {
                emptyBody(lines, request, in, "the spec's example holds ${, which would be read as a placeholder");
                return;
            }
            var lineCount = (int) printed.lines().count();
            if (lineCount <= options.bodyFileLines()) {
                lines.add(in + "raw: |");
                for (var line : printed.split("\n", -1)) {
                    lines.add(in + "  " + line);
                }
                return;
            }
            var base = BODIES + "/" + orFallback(ImportNames.fileName(request), "body");
            var file = ImportNames.unique(List.of(base), "-", bodyFiles).getFirst();
            bodyFiles.add(file);
            var path = file + ".json";
            bodies.put(path, printed + "\n");
            lines.add(
                    in + "file: " + YamlText.scalar(path),
                    lineCount + " lines: longer than --body-file-lines (" + options.bodyFileLines() + ")");
        }

        private void emptyBody(Lines lines, String request, String in, String reason) {
            lines.add(in + "raw: \"\"", reason + ": write the body here");
            note(suiteNotes, "\"" + request + "\": " + reason + "; write the body");
        }

        private void writeFormBody(Lines lines, MediaType media, String request, String in) {
            if (media.properties().isEmpty()) {
                lines.add(in + "form: {}", "the spec names no fields: add them here");
                note(suiteNotes, "\"" + request + "\": the spec names no form fields; add them");
                return;
            }
            lines.add(in + "form:");
            var example = media.example();
            for (var property : media.properties()) {
                var value = example != null && example.isObject() ? exampleText(example.get(property)) : null;
                var key = in + "  " + YamlText.scalar(property) + ": ";
                if (value == null || value.contains(PLACEHOLDER)) {
                    lines.add(key + "\"\"", "fill in");
                    note(suiteNotes, "\"" + request + "\": fill in the form field " + property);
                } else {
                    lines.add(key + YamlText.scalar(value));
                }
            }
        }

        /** Prints JSON with two spaces of indent, {@code "key": value}, one member or element per line. */
        private static String printed(JsonNode node, String indent) {
            if (node.isObject()) {
                if (node.isEmpty()) {
                    return "{}";
                }
                var members = new ArrayList<String>();
                for (var property : node.properties()) {
                    members.add(indent + "  " + JSON.writeValueAsString(property.getKey()) + ": "
                            + printed(property.getValue(), indent + "  "));
                }
                return "{\n" + String.join(",\n", members) + "\n" + indent + "}";
            }
            if (node.isArray()) {
                if (node.isEmpty()) {
                    return "[]";
                }
                var elements = new ArrayList<String>();
                for (var element : node) {
                    elements.add(indent + "  " + printed(element, indent + "  "));
                }
                return "[\n" + String.join(",\n", elements) + "\n" + indent + "]";
            }
            return JSON.writeValueAsString(node);
        }

        // --- the assertion ---

        private static OptionalInt successStatus(List<String> codes) {
            return codes.stream()
                    .filter(code -> code.matches("2[0-9][0-9]"))
                    .mapToInt(Integer::parseInt)
                    .min();
        }

        // --- environments ---

        private List<String> environments(Map<String, String> files) {
            var servers = document.servers();
            if (servers.isEmpty()) {
                var lines = new Lines();
                var file = LOCAL + ENV_FILE;
                lines.add("baseUrl: \"\"", "the spec names no server: fill in its url");
                noteIn(envNotes, file, "baseUrl: the spec names no server; fill in its url");
                writeTokenEnv(lines, file);
                files.put(file, lines.text());
                return List.of(LOCAL);
            }
            var wanted = new ArrayList<String>();
            for (var i = 0; i < servers.size(); i++) {
                wanted.add(orFallback(ImportNames.fileName(servers.get(i).description()), "server-" + (i + 1)));
            }
            var names = ImportNames.unique(wanted, "-", Set.of(BODIES));
            for (var i = 0; i < servers.size(); i++) {
                var file = names.get(i) + ENV_FILE;
                var lines = envLines(servers.get(i), file);
                writeTokenEnv(lines, file);
                files.put(file, lines.text());
            }
            return names;
        }

        private void writeTokenEnv(Lines lines, String file) {
            for (var request : auth.tokenRequests()) {
                lines.add(YamlText.scalar(request.tokenUrlKey()) + ": " + YamlText.scalar(request.tokenUrl()));
                lines.add(
                        YamlText.scalar(request.clientIdKey()) + ": \"\"",
                        "your client's id; empty counts as missing, so a run fails naming it until you fill it in");
                noteIn(envNotes, file, request.clientIdKey() + ": fill in your client's id");
            }
        }

        private Lines envLines(Server server, String file) {
            var url = server.url();
            var resolved = VARIABLE.matcher(url).replaceAll(match -> {
                var value = server.variableDefaults().get(match.group(1));
                return Matcher.quoteReplacement(value == null ? match.group() : value);
            });
            String reason = null;
            if (!SCHEME.matcher(resolved).matches()) {
                reason = "the spec's server url " + url + " is relative to where the spec is served";
            } else if (VARIABLE.matcher(resolved).find()) {
                reason = "the spec's server url " + url + " has a variable without a default";
            }
            var lines = new Lines();
            if (reason != null) {
                lines.add("baseUrl: \"\"", YamlText.commentText(reason) + ": fill in");
                noteIn(envNotes, file, "baseUrl: " + reason + "; fill in the url");
            } else {
                lines.add("baseUrl: " + YamlText.scalar(StringUtils.removeEnd(resolved, "/")));
            }
            return lines;
        }

        // --- helpers ---

        private void note(List<Note> notes, String message) {
            notes.add(new Note(suiteFile, message));
        }

        private void note(List<Note> notes, String message, String secret) {
            notes.add(new Note(suiteFile, message, secret));
        }

        private static void noteIn(List<Note> notes, String file, String message) {
            notes.add(new Note(file, message));
        }
    }

    private static String orFallback(String name) {
        return orFallback(name, FALLBACK_NAME);
    }

    private static String orFallback(String name, String fallback) {
        return name.isEmpty() ? fallback : name;
    }

    /** Percent-encodes, as UTF-8, every character but an ASCII letter, a digit and {@code -._~}. */
    private static String percentEncoded(String text) {
        var result = new StringBuilder(text.length());
        for (var b : text.getBytes(StandardCharsets.UTF_8)) {
            var c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || "-._~".indexOf(c) >= 0) {
                result.append(c);
            } else {
                result.append('%').append(String.format("%02X", (int) c));
            }
        }
        return result.toString();
    }

    /** The lines of a YAML document, each optionally followed by a comment, and joined by newlines. */
    private static final class Lines {

        private final List<String> lines = new ArrayList<>();

        void add(String content) {
            lines.add(content);
        }

        void add(String content, String comment) {
            if (comment == null) {
                lines.add(content);
                return;
            }
            lines.add(padded(content) + "# " + comment);
        }

        /** A comment on a line of its own, at the comment column. */
        void comment(String comment) {
            lines.add(" ".repeat(COMMENT_COLUMN) + "# " + comment);
        }

        void blank() {
            if (!lines.isEmpty() && !lines.getLast().isEmpty()) {
                lines.add("");
            }
        }

        String text() {
            var end = lines.size();
            while (end > 0 && lines.get(end - 1).isEmpty()) {
                end--;
            }
            return String.join("\n", lines.subList(0, end)) + "\n";
        }

        private static String padded(String content) {
            return content.length() < COMMENT_COLUMN - 1
                    ? content + " ".repeat(COMMENT_COLUMN - content.length())
                    : content + "  ";
        }
    }
}
