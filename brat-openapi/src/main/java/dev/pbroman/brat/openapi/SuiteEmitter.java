package dev.pbroman.brat.openapi;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import dev.pbroman.brat.openapi.OpenApiDocument.Field;
import dev.pbroman.brat.openapi.OpenApiDocument.MediaType;
import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.Parameter;
import dev.pbroman.brat.openapi.OpenApiDocument.Schema;
import dev.pbroman.brat.openapi.OpenApiDocument.Server;
import dev.pbroman.brat.openapi.Scaffold.Note;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.BooleanNode;
import tools.jackson.databind.node.DecimalNode;
import tools.jackson.databind.node.IntNode;
import tools.jackson.databind.node.StringNode;

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

    /** How many optional form fields a comment names before {@code … and N more}. */
    private static final int MAX_OPTIONAL_NAMED = 10;

    /** How many requests a server key's comment names before {@code and N more}. */
    private static final int MAX_SERVER_REQUESTS_NAMED = 3;

    /** The longest environment directory name taken from a description. */
    private static final int MAX_ENVIRONMENT_NAME = 30;

    private static final Set<String> ARTICLES = Set.of("a", "an", "the");

    /** Words an environment name does not end on. */
    private static final Set<String> FILLER_WORDS =
            Set.of("a", "an", "the", "and", "or", "of", "for", "to", "in", "on", "at", "with", "by");

    /** A path segment that names a version: {@code v1}, {@code v2beta}, {@code 2010-04-01}. */
    private static final Pattern VERSION_SEGMENT = Pattern.compile("(?i)v[0-9]+[a-z0-9]*|[0-9]{4}-[0-9]{2}-[0-9]{2}");

    /** How many enum values a constant's comment names before {@code …}. */
    private static final int MAX_ENUM_NAMED = 5;

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

    /**
     * A constant: its name, the parameter it was first met as, the request that first sends it, the
     * first request of the bare-named constant it was split from ({@code null} for a bare one), and every
     * use in spec order.
     */
    private record Constant(String name, Parameter parameter, String firstRequest, String splitFrom, List<Use> uses) {}

    /** One request's use of a constant: where it sends it and the example it gives. */
    private record Use(String request, String in, JsonNode example) {}

    /**
     * What a JSON body is written as: printed {@code json}, else {@code raw} text as it is, else nothing;
     * {@code reason} comments a {@code raw} or the empty body, {@code bodyComment} the {@code body:} line,
     * and {@code fillIn} names the blanks a built body holds.
     */
    private record JsonBody(JsonNode json, String raw, String reason, String bodyComment, List<String> fillIn) {}

    /** The requests under one parent: the top of the suite, or one sub-suite. */
    private record Group(String name, List<Operation> operations) {}

    /** One emission, holding what it has written so far. */
    private static final class Emission {

        private final OpenApiDocument document;
        private final String sourceName;
        private final ImportOptions options;

        private final Map<Operation, String> requestNames = new IdentityHashMap<>();
        private final Map<String, Constant> constants = new LinkedHashMap<>();
        private final Map<String, String> constantByParameter = new HashMap<>();
        private final Set<String> bodyFiles = new HashSet<>();
        private final Map<String, String> bodies = new LinkedHashMap<>();
        private final List<Note> suiteNotes = new ArrayList<>();
        private final List<Note> envNotes = new ArrayList<>();
        private final Set<String> notedSecrets = new HashSet<>();
        private final List<String> tokenRequestNames = new ArrayList<>();
        private ImportAuth.Plan auth;
        private ImportAuth.Auth suiteWide;
        private ImportAuth.Auth hoisted;
        private final Map<Operation, String> serverBases = new IdentityHashMap<>();
        private final Map<String, String> serverKeys = new LinkedHashMap<>();
        private final Map<String, List<Operation>> serverOperations = new HashMap<>();
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
            if (tagged.isEmpty()) {
                groupByPathSegment(top, tagged);
            }
            auth = ImportAuth.declaresSecurity(document) ? ImportAuth.plan(document) : undeclared();
            var subSuites = nameGroups(top, tagged);
            collectOperationServers();
            collectConstants();

            var suite = suiteText(top, subSuites);
            var files = new LinkedHashMap<String, String>();
            files.put(suiteFile, suite);
            files.putAll(bodies);
            var environments = environments(files);

            for (var entry : document.notImported()) {
                note(suiteNotes, "not imported: " + entry);
            }
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
        /**
         * Where no operation is tagged: moves each operation of {@code top} whose path has a first segment
         * that is neither a parameter nor a version into {@code tagged} under that segment, leaving an
         * operation at the top where its segment would hold it alone.
         */
        private static void groupByPathSegment(List<Operation> top, Map<String, List<Operation>> tagged) {
            var bySegment = new LinkedHashMap<String, List<Operation>>();
            for (var operation : top) {
                var segment = firstSegment(operation.path());
                if (segment != null) {
                    bySegment.computeIfAbsent(segment, key -> new ArrayList<>()).add(operation);
                }
            }
            var moved = Collections.newSetFromMap(new IdentityHashMap<Operation, Boolean>());
            for (var group : bySegment.entrySet()) {
                if (group.getValue().size() > 1
                        && !ImportNames.nodeName(group.getKey()).isEmpty()) {
                    tagged.put(group.getKey(), group.getValue());
                    moved.addAll(group.getValue());
                }
            }
            top.removeIf(moved::contains);
        }

        /** The first segment of a path that is neither a parameter nor a version, or {@code null}. */
        private static String firstSegment(String path) {
            for (var segment : path.split("/")) {
                if (!segment.isEmpty()
                        && !PATH_PARAMETER.matcher(segment).find()
                        && !VERSION_SEGMENT.matcher(segment).matches()) {
                    return segment;
                }
            }
            return null;
        }

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

        /**
         * Gathers the constants as {@link OpenApiImporter#scaffold} describes — one per parameter name and
         * identity, named, in order of first use — filling {@link #constants} and
         * {@link #constantByParameter}.
         */
        private void collectConstants() {
            for (var operation : document.operations()) {
                var request = requestNames.get(operation);
                for (var parameter : sentParameters(operation)) {
                    var key = parameterKey(parameter);
                    var name = constantByParameter.get(key);
                    if (name == null) {
                        // Split only when the bare name is taken, so whoever holds it is there to name.
                        var bare = constants.get(parameter.name());
                        name = bare == null ? parameter.name() : splitName(parameter, operation);
                        var splitFrom = bare == null ? null : bare.firstRequest();
                        constants.put(name, new Constant(name, parameter, request, splitFrom, new ArrayList<>()));
                        constantByParameter.put(key, name);
                    }
                    constants.get(name).uses().add(new Use(request, parameter.in(), parameter.example()));
                }
            }
        }

        /**
         * The name for a parameter whose bare name is taken: {@code <name>_<segment>} from the last fixed
         * segment of the operation's path, else the lowest free {@code <name>_2}, {@code <name>_3}….
         */
        private String splitName(Parameter parameter, Operation operation) {
            var segments = operation.path().split("/");
            for (var i = segments.length - 1; i >= 0; i--) {
                if (segments[i].isEmpty() || PATH_PARAMETER.matcher(segments[i]).find()) {
                    continue;
                }
                var suffix = StringUtils.strip(segments[i].replaceAll("[^A-Za-z0-9]+", "_"), "_");
                var candidate = parameter.name() + "_" + suffix;
                if (!suffix.isEmpty() && !constants.containsKey(candidate)) {
                    return candidate;
                }
                break;
            }
            for (var n = 2; ; n++) {
                var candidate = parameter.name() + "_" + n;
                if (!constants.containsKey(candidate)) {
                    return candidate;
                }
            }
        }

        /** The name of the constant {@code parameter} is sent from; collected first. */
        private String constantName(Parameter parameter) {
            return constantByParameter.get(parameterKey(parameter));
        }

        /** What tells two parameters' constants apart: the name and the identity. */
        private static String parameterKey(Parameter parameter) {
            return parameter.name() + "\u0000" + parameter.identity();
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

        /**
         * Writes the {@code constants:} block as {@link OpenApiImporter#scaffold} describes: each
         * constant's value, its comment, a commented-out line for one without a value, and the notes.
         */
        private void writeConstants(Lines lines) {
            if (constants.isEmpty()) {
                return;
            }
            var anyValue = constants.values().stream()
                    .anyMatch(constant -> usableUse(constant) != null
                            || !constant.parameter().enumValues().isEmpty());
            if (anyValue) {
                lines.add("constants:");
            } else {
                lines.add("# constants:", "none has a value from the spec: uncomment this with the first you fill in");
            }
            for (var constant : constants.values()) {
                writeConstant(lines, constant);
            }
            lines.blank();
        }

        private void writeConstant(Lines lines, Constant constant) {
            var name = constant.name();
            var usable = usableUse(constant);
            var enumValues = constant.parameter().enumValues();
            if (usable == null && enumValues.isEmpty()) {
                var reason = missingReason(constant.uses().getFirst().example());
                lines.add("  # " + YamlText.scalar(name) + ":", reason + ": fill in, or -p constants." + name + "=…");
                note(
                        suiteNotes,
                        "constants." + name + ": " + reason + "; fill in a value, or pass -p constants." + name + "=…");
                return;
            }
            String text;
            String in;
            String source;
            if (usable != null) {
                text = exampleText(usable.example());
                in = usable.in();
                source = "example from the spec";
            } else {
                text = enumValues.getFirst();
                in = constant.parameter().in();
                var named = enumValues.size() > MAX_ENUM_NAMED
                        ? String.join(", ", enumValues.subList(0, MAX_ENUM_NAMED)) + ", …"
                        : String.join(", ", enumValues);
                source = "the first of " + named;
            }
            var value = "header".equals(in) ? text : percentEncoded(text);
            var comment = value.equals(text)
                    ? source
                    : source.replace("example from the spec", "the spec's example") + ", encoded: " + text;
            if (constant.splitFrom() != null) {
                comment += "; the \"" + constant.parameter().name() + "\" of \"" + constant.firstRequest() + "\", not "
                        + constant.splitFrom() + "'";
            }
            lines.add("  " + YamlText.scalar(name) + ": " + YamlText.scalar(value), YamlText.commentText(comment));
            var differing = new ArrayList<String>();
            for (var use : constant.uses()) {
                var other = exampleText(use.example());
                if (use != usable && other != null && !other.equals(text)) {
                    differing.add("\"" + use.request() + "\" uses " + other);
                }
            }
            if (!differing.isEmpty()) {
                var others = String.join(", ", differing);
                var whose =
                        usable == constant.uses().getFirst() ? "first request's example; " : "first usable example; ";
                lines.comment(whose + YamlText.commentText(others));
                note(
                        suiteNotes,
                        "constants." + name + " serves requests whose examples differ (" + others + "); it holds the "
                                + whose.substring(0, whose.length() - 2));
            }
        }

        /** The first use whose example is a single value holding no placeholder, or {@code null}. */
        private static Use usableUse(Constant constant) {
            for (var use : constant.uses()) {
                var text = exampleText(use.example());
                if (text != null && !text.contains(PLACEHOLDER)) {
                    return use;
                }
            }
            return null;
        }

        /** Why an example gives a constant no value. */
        private static String missingReason(JsonNode example) {
            if (example == null) {
                return "no example in the spec";
            }
            if (example.isContainer()) {
                return "the spec's example is not a single value";
            }
            return "the spec's example holds ${, which would be read as a placeholder";
        }

        // --- the suite ---

        private String suiteText(List<Operation> top, List<Group> subSuites) {
            var lines = new Lines();
            lines.add("# " + header());
            if (document.operations().isEmpty()
                    && document.notImported().stream().anyMatch(entry -> entry.startsWith("webhooks:"))) {
                lines.add("# The spec defines only webhooks, which are not imported.");
            }
            lines.add("name: " + YamlText.scalar(orFallback(ImportNames.nodeName(document.title()))));
            lines.blank();
            writeConstants(lines);
            suiteWide = suiteWideAuth();
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
                    hoisted = suiteWide == null ? sharedAuth(group.operations()) : null;
                    if (hoisted != null) {
                        writeAuth(
                                lines,
                                "    ",
                                hoisted.block(),
                                "the spec's security for every request here: " + hoisted.source(),
                                hoisted.todo(),
                                "\"" + group.name() + "\"");
                    }
                    lines.add("    requests:");
                    writeRequests(lines, group.operations(), "    ");
                    hoisted = null;
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
            if (carriesOwnAuth(own) && hoisted == null && suiteWide == null) {
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
            var media = sendsBody(operation, name) ? chosenMedia(operation.requestBody()) : null;
            var headers = new LinkedHashMap<String, String>();
            for (var parameter : operation.parameters()) {
                if ("header".equals(parameter.in()) && isSent(parameter)) {
                    headers.put(parameter.name(), "${constants." + constantName(parameter) + "}");
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
                var json = isJson(media.type()) ? jsonBody(media, name) : null;
                lines.add(in + "  body:", json == null ? null : json.bodyComment());
                writeBody(lines, media, json, name, in + "    ");
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

        /**
         * The {@code auth} every request of {@code operations} carries of its own, where there are at least
         * two and all carry the same one; else {@code null}.
         */
        private ImportAuth.Auth sharedAuth(List<Operation> operations) {
            if (operations.size() < 2) {
                return null;
            }
            ImportAuth.Auth shared = null;
            for (var operation : operations) {
                var own = auth.requests().get(operation);
                if (!carriesOwnAuth(own)
                        || own.block() == null
                        || ImportAuth.NONE.equals(own.block().type())
                        || own.queryName() != null) {
                    return null;
                }
                if (shared == null) {
                    shared = own;
                } else if (!Objects.equals(shared.block(), own.block())
                        || !Objects.equals(shared.source(), own.source())
                        || !Objects.equals(shared.todo(), own.todo())) {
                    return null;
                }
            }
            return shared;
        }

        /**
         * The {@code auth} to write once on the suite: where the document declares no security, there is
         * no token request, and every request carries the same one of its own — at least two. Else
         * {@code null}.
         */
        private ImportAuth.Auth suiteWideAuth() {
            if (!document.security().isEmpty() || !auth.tokenRequests().isEmpty()) {
                return null;
            }
            return sharedAuth(document.operations());
        }

        private void writeSuiteAuth(Lines lines) {
            if (suiteWide != null) {
                writeAuth(
                        lines,
                        "",
                        suiteWide.block(),
                        "the spec's security for every request: " + suiteWide.source(),
                        suiteWide.todo(),
                        "the suite");
                lines.blank();
                return;
            }
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
            lines.add(in + "  type: " + block.type(), todo == null ? null : YamlText.commentText(todo));
            if (todo != null) {
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

        private String url(Operation operation) {
            var pathConstants = new HashMap<String, String>();
            for (var parameter : sentParameters(operation)) {
                if ("path".equals(parameter.in())) {
                    pathConstants.put(parameter.name(), constantName(parameter));
                }
            }
            var path = PATH_PARAMETER
                    .matcher(operation.path())
                    .replaceAll(match ->
                            Matcher.quoteReplacement("${constants." + pathConstants.get(match.group(1)) + "}"));
            var query = new ArrayList<String>();
            for (var parameter : operation.parameters()) {
                if ("query".equals(parameter.in()) && parameter.required()) {
                    query.add(percentEncoded(parameter.name()) + "=${constants." + constantName(parameter) + "}");
                }
            }
            return serverBases.getOrDefault(operation, "${env.baseUrl}")
                    + path
                    + (query.isEmpty() ? "" : "?" + String.join("&", query));
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

        /**
         * Whether the request sends a body: not for a {@code GET} or {@code HEAD}, nor for an empty form
         * without an example; a note says so where the spec declared one.
         */
        private boolean sendsBody(Operation operation, String request) {
            if (operation.requestBody().isEmpty()) {
                return false;
            }
            if ("GET".equals(operation.method()) || "HEAD".equals(operation.method())) {
                note(
                        suiteNotes,
                        "\"" + request + "\": the spec declares a body on a " + operation.method()
                                + ", which is not sent");
                return false;
            }
            var media = chosenMedia(operation.requestBody());
            if (isForm(media.type()) && media.properties().isEmpty() && media.example() == null) {
                note(suiteNotes, "\"" + request + "\": the spec declares an empty form, which is not sent");
                return false;
            }
            return true;
        }

        /** The media type a body is written for, of a non-empty list. */
        private static MediaType chosenMedia(List<MediaType> media) {
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

        private void writeBody(Lines lines, MediaType media, JsonBody json, String request, String in) {
            if (json != null) {
                writeJsonBody(lines, json, request, in);
            } else if (isForm(media.type())) {
                writeFormBody(lines, media, request, in);
            } else {
                var reason = "write the " + media.type() + " body here";
                lines.add(in + "raw: \"\"", YamlText.commentText(reason));
                note(suiteNotes, "\"" + request + "\": " + reason);
            }
        }

        /**
         * What a JSON media type's body is written as, as {@link OpenApiImporter#scaffold} describes: the
         * example, read or encoded as JSON; a {@code serializedValue} as it is; else a body built from
         * the schema; else nothing, with the reason.
         */
        private JsonBody jsonBody(MediaType media, String request) {
            var example = media.example();
            if (example != null) {
                if (media.serialized()) {
                    return new JsonBody(
                            null, example.asString(), "the spec's serialized example, as it is", null, List.of());
                }
                var json = example;
                if (example.isString()) {
                    JsonNode parsed = null;
                    try {
                        parsed = JSON.readTree(example.asString());
                    } catch (JacksonException notJson) {
                        // text that is not JSON is a JSON string, which `json` already is
                    }
                    json = parsed != null && parsed.isContainer() ? parsed : example;
                }
                return new JsonBody(json, null, null, null, List.of());
            }
            var fillIn = new ArrayList<String>();
            var built = media.schema() == null ? null : builtFromSchema(media.schema(), "", fillIn);
            if (built == null) {
                return new JsonBody(null, null, "no example in the spec", null, List.of());
            }
            var comment = "built from the schema's examples";
            if (!fillIn.isEmpty()) {
                comment += "; fill in: " + String.join(", ", fillIn);
                note(
                        suiteNotes,
                        "\"" + request + "\": the body is built from the schema's examples; fill in "
                                + String.join(", ", fillIn));
            }
            return new JsonBody(built, null, null, YamlText.commentText(comment), List.copyOf(fillIn));
        }

        /**
         * A value built from {@code schema} as {@link OpenApiImporter#scaffold} describes, adding to
         * {@code fillIn} the path of each required property written blank; {@code null} when it yields
         * nothing.
         */
        private static JsonNode builtFromSchema(Schema schema, String path, List<String> fillIn) {
            var value = valueOf(schema);
            if (value != null) {
                return value;
            }
            if (isObject(schema)) {
                var own = new ArrayList<String>();
                var object = JSON.createObjectNode();
                var hasValue = false;
                for (var field : schema.properties()) {
                    var fieldPath = path.isEmpty() ? field.name() : path + "." + field.name();
                    var child = builtFromSchema(field.schema(), fieldPath, own);
                    hasValue |= child != null;
                    if (child == null && field.required()) {
                        child = blank(field.schema(), fieldPath, own);
                    }
                    if (child != null) {
                        object.set(field.name(), child);
                    }
                }
                // A nested object holding only blanks is as good as no value; the body itself keeps them.
                if (object.isEmpty() || (!hasValue && !path.isEmpty())) {
                    return null;
                }
                fillIn.addAll(own);
                return object;
            }
            if ("array".equals(schema.type()) && schema.items() != null) {
                var item = builtFromSchema(schema.items(), path, fillIn);
                return item == null ? null : JSON.createArrayNode().add(item);
            }
            return null;
        }

        /** A required value without one: a blank of its type, an object of its own required properties. */
        private static JsonNode blank(Schema schema, String path, List<String> fillIn) {
            if (isObject(schema)) {
                var object = JSON.createObjectNode();
                for (var field : schema.properties()) {
                    if (field.required()) {
                        var fieldPath = path + "." + field.name();
                        var child = builtFromSchema(field.schema(), fieldPath, fillIn);
                        object.set(field.name(), child != null ? child : blank(field.schema(), fieldPath, fillIn));
                    }
                }
                if (object.isEmpty()) {
                    fillIn.add(path);
                }
                return object;
            }
            fillIn.add(path);
            return switch (String.valueOf(schema.type())) {
                case "array" -> JSON.createArrayNode();
                case "integer", "number" -> IntNode.valueOf(0);
                case "boolean" -> BooleanNode.FALSE;
                default -> StringNode.valueOf("");
            };
        }

        /** A schema's own value: its example, else its first enum value typed by its type; else {@code null}. */
        private static JsonNode valueOf(Schema schema) {
            if (schema.example() != null) {
                return schema.example();
            }
            if (schema.enumValues().isEmpty()) {
                return null;
            }
            var text = schema.enumValues().getFirst();
            try {
                return switch (String.valueOf(schema.type())) {
                    case "integer", "number" -> DecimalNode.valueOf(new BigDecimal(text));
                    case "boolean" -> BooleanNode.valueOf(Boolean.parseBoolean(text));
                    default -> StringNode.valueOf(text);
                };
            } catch (NumberFormatException notANumber) {
                return StringNode.valueOf(text);
            }
        }

        private static boolean isObject(Schema schema) {
            return "object".equals(schema.type()) || !schema.properties().isEmpty();
        }

        private void writeJsonBody(Lines lines, JsonBody body, String request, String in) {
            if (body.raw() != null) {
                if (body.raw().contains(PLACEHOLDER)) {
                    emptyBody(lines, request, in, "the spec's example holds ${, which would be read as a placeholder");
                    return;
                }
                lines.add(in + "raw: " + YamlText.scalar(body.raw()), body.reason());
                return;
            }
            if (body.json() == null) {
                emptyBody(lines, request, in, body.reason());
                return;
            }
            var json = body.json();
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
            var fields =
                    media.schema() == null ? List.<Field>of() : media.schema().properties();
            var required = fields.stream().filter(Field::required).toList();
            var optional = fields.stream()
                    .filter(field -> !field.required())
                    .map(Field::name)
                    .toList();
            String comment = null;
            if (!optional.isEmpty()) {
                var named = optional.size() > MAX_OPTIONAL_NAMED
                        ? String.join(", ", optional.subList(0, MAX_OPTIONAL_NAMED)) + " … and "
                                + (optional.size() - MAX_OPTIONAL_NAMED) + " more"
                        : String.join(", ", optional);
                comment = YamlText.commentText("optional: " + named);
            }
            var example = media.example();
            if (fields.isEmpty() && example != null && example.isObject() && !example.isEmpty()) {
                lines.add(in + "form:", "the spec's example; its schema names no fields");
                for (var entry : example.properties()) {
                    writeFormEntry(lines, in, entry.getKey(), entry.getValue().isContainer(), example, request);
                }
                return;
            }
            if (fields.isEmpty()) {
                lines.add(in + "form: {}", "the spec names no fields: add them here");
                note(suiteNotes, "\"" + request + "\": the spec names no form fields; add them");
                return;
            }
            if (required.isEmpty()) {
                lines.add(in + "form: {}", comment);
                return;
            }
            lines.add(in + "form:", comment);
            for (var field : required) {
                writeFormEntry(
                        lines,
                        in,
                        field.name(),
                        isObject(field.schema())
                                || "array".equals(field.schema().type()),
                        example,
                        request);
            }
        }

        /**
         * One form entry: the example's value for it, or {@code ""} with a comment and a note — saying to
         * send it as {@code name[<key>]} entries where it is nested.
         */
        private void writeFormEntry(
                Lines lines, String in, String property, boolean nested, JsonNode example, String request) {
            var key = in + "  " + YamlText.scalar(property) + ": ";
            if (nested) {
                lines.add(key + "\"\"", YamlText.commentText("nested: send as " + property + "[<key>] entries"));
                note(
                        suiteNotes,
                        "\"" + request + "\": send the form field " + property + " as " + property + "[<key>] entries");
                return;
            }
            var value = example != null && example.isObject() ? exampleText(example.get(property)) : null;
            if (value == null || value.contains(PLACEHOLDER)) {
                lines.add(key + "\"\"", "fill in");
                note(suiteNotes, "\"" + request + "\": fill in the form field " + property);
            } else {
                lines.add(key + YamlText.scalar(value));
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

        /**
         * Gives each operation naming a server of its own, other than a document server, the base its url
         * starts with, filling {@link #serverBases} and {@link #serverKeys} (url to {@code env} key).
         */
        private void collectOperationServers() {
            var documentUrls = new HashSet<String>();
            for (var server : document.servers()) {
                documentUrls.add(withoutTrailingSlash(resolvedUrl(server)));
            }
            for (var operation : document.operations()) {
                if (operation.servers().isEmpty()) {
                    continue;
                }
                var url = withoutTrailingSlash(resolvedUrl(operation.servers().getFirst()));
                if (documentUrls.contains(url)) {
                    continue;
                }
                if (!SCHEME.matcher(url).matches()) {
                    serverBases.put(
                            operation, "${env.baseUrl}" + (url.isEmpty() || url.startsWith("/") ? url : "/" + url));
                    continue;
                }
                var key = serverKeys.computeIfAbsent(url, this::serverKey);
                serverOperations
                        .computeIfAbsent(url, ignored -> new ArrayList<>())
                        .add(operation);
                serverBases.put(operation, "${env." + key + "}");
            }
        }

        /** A url without its trailing {@code /}, unless that is all its scheme's {@code ://} leaves. */
        private static String withoutTrailingSlash(String url) {
            return url.endsWith("://") ? url : StringUtils.removeEnd(url, "/");
        }

        /** The host of a url with a scheme: what follows {@code ://} up to a path, port, query or fragment. */
        private static String hostOf(String url) {
            return url.substring(url.indexOf("://") + 3).split("[/:?#]", 2)[0];
        }

        /** {@code <label>BaseUrl} for a url, numbered past the keys already given. */
        private String serverKey(String url) {
            var host = hostOf(url);
            var label = host.split("\\.", 2)[0];
            var clean =
                    label.contains("{") ? "" : label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            var base = (clean.isEmpty() ? "server" : clean) + "BaseUrl";
            var key = base;
            for (var n = 2; serverKeys.containsValue(key); n++) {
                key = base + n;
            }
            return key;
        }

        /** Writes each operation server's {@code env} key, its url and its comment, to one environment. */
        private void writeServerEnv(Lines lines, String file) {
            for (var entry : serverKeys.entrySet()) {
                var url = entry.getKey();
                var key = entry.getValue();
                var names = serverOperations.get(url).stream()
                        .map(requestNames::get)
                        .toList();
                var named = names.size() > MAX_SERVER_REQUESTS_NAMED
                        ? String.join(", ", names.subList(0, MAX_SERVER_REQUESTS_NAMED)) + " and "
                                + (names.size() - MAX_SERVER_REQUESTS_NAMED) + " more"
                        : String.join(", ", names);
                if (VARIABLE.matcher(url).find() || hostOf(url).isEmpty()) {
                    var reason = "the spec's server url " + url
                            + (hostOf(url).isEmpty() ? " names no host" : " has a variable without a default");
                    lines.add(YamlText.scalar(key) + ": \"\"", YamlText.commentText(reason) + ": fill in");
                    noteIn(envNotes, file, key + ": " + reason + "; fill in the url");
                } else {
                    lines.add(
                            YamlText.scalar(key) + ": " + YamlText.scalar(url),
                            YamlText.commentText(
                                    "the spec's server for: " + named + " — the same in every environment"));
                }
            }
        }

        /** The directory name a server's description gives, before names are made unique. */
        private static String environmentName(Server server, int index) {
            var words = new ArrayList<String>();
            if (server.description() != null) {
                words.addAll(List.of(server.description().strip().split("\\s+")));
            }
            if (words.size() > 1 && ARTICLES.contains(words.getFirst().toLowerCase(Locale.ROOT))) {
                words.removeFirst();
            }
            var kept = new ArrayList<>(words.subList(0, Math.min(4, words.size())));
            while (kept.size() > 1
                    && FILLER_WORDS.contains(
                            kept.getLast().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""))) {
                kept.removeLast();
            }
            var name = ImportNames.fileName(String.join(" ", kept));
            if (name.length() > MAX_ENVIRONMENT_NAME) {
                var end = name.lastIndexOf('-', MAX_ENVIRONMENT_NAME);
                name = end > 0 ? name.substring(0, end) : name.substring(0, MAX_ENVIRONMENT_NAME);
            }
            return name.isEmpty() ? "server-" + (index + 1) : name;
        }

        private List<String> environments(Map<String, String> files) {
            var servers = document.servers();
            if (servers.isEmpty()) {
                var lines = new Lines();
                var file = LOCAL + ENV_FILE;
                lines.add("baseUrl: \"\"", "the spec names no server: fill in its url");
                noteIn(envNotes, file, "baseUrl: the spec names no server; fill in its url");
                writeServerEnv(lines, file);
                writeTokenEnv(lines, file);
                files.put(file, lines.text());
                return List.of(LOCAL);
            }
            var wanted = new ArrayList<String>();
            for (var i = 0; i < servers.size(); i++) {
                wanted.add(environmentName(servers.get(i), i));
            }
            var names = ImportNames.unique(wanted, "-", Set.of(BODIES));
            for (var i = 0; i < servers.size(); i++) {
                var file = names.get(i) + ENV_FILE;
                var lines = envLines(servers.get(i), file);
                writeServerEnv(lines, file);
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

        /** A server's url, each variable that has a default replaced by it. */
        private static String resolvedUrl(Server server) {
            return VARIABLE.matcher(server.url()).replaceAll(match -> {
                var value = server.variableDefaults().get(match.group(1));
                return Matcher.quoteReplacement(value == null ? match.group() : value);
            });
        }

        private Lines envLines(Server server, String file) {
            var url = server.url();
            var resolved = resolvedUrl(server);
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
