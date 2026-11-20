package dev.pbroman.brat.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.pbroman.brat.openapi.OpenApiDocument.Operation;
import dev.pbroman.brat.openapi.OpenApiDocument.SecurityScheme;
import org.apache.commons.lang3.StringUtils;

/**
 * Static helpers deciding how an imported suite authenticates: the auth the suite declares, the auth
 * each operation's request is sent with, and the setup requests that fetch a token.
 */
final class ImportAuth {

    /** The type of an auth block that sends no credentials. */
    static final String NONE = "none";

    private static final String EMPTY_SECURITY = "security: []";

    private static final String TOKEN_REQUEST = "get a token";

    private ImportAuth() {
        // static helpers only
    }

    /**
     * An {@code auth:} block as written: its type and its args.
     *
     * @param type the auth type ({@code bearer}, {@code basic}, {@code apikey}, {@code none})
     * @param args the args in the order written, each value as the suite holds it
     *        ({@code ${secrets.token}}); empty for {@code none}
     */
    record Block(String type, Map<String, String> args) {

        /**
         * Constructs a block, keeping a copy of its args in their order.
         *
         * @param type the auth type
         * @param args the args; copied
         */
        Block {
            args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
        }
    }

    /**
     * How a request is sent, or how the suite declares it is.
     *
     * @param block the auth block, or {@code null} for none at all: no credentials, and nothing that
     *        cancels an inherited auth either
     * @param queryName for an api key sent in the query, the parameter's name; otherwise {@code null}
     * @param querySecret for an api key sent in the query, the key of the secret it is sent from;
     *        otherwise {@code null}
     * @param source what in the spec this came from, for a comment: the scheme's name
     *        ({@code clientCredentials}), {@code security: []}, or {@code null} where the spec declares no
     *        security
     * @param todo what the author has to do about it, for a comment and a note — a scheme that is not
     *        imported, or a token to obtain by hand; {@code null} if nothing
     */
    record Auth(Block block, String queryName, String querySecret, String source, String todo) {

        /** Sending no credentials, because the spec declares no security. */
        static final Auth UNDECLARED = new Auth(null, null, null, null, null);
    }

    /**
     * A setup request fetching a token by OAuth 2 client credentials.
     *
     * @param name the name the request asks for; a sibling may already have it, and the emitter makes it
     *        unique, the token requests claiming theirs before the operations' requests
     * @param scheme the name of the security scheme it serves
     * @param tokenUrlKey the {@code env} key holding the token url
     * @param tokenUrl the token url as the spec gives it
     * @param clientIdKey the {@code env} key holding the client id
     * @param clientSecretKey the {@code secrets} key holding the client secret
     * @param scope the scopes to request, separated by spaces; empty to request none
     * @param accessTokenVar the {@code vars} key the token is captured into
     */
    record TokenRequest(
            String name,
            String scheme,
            String tokenUrlKey,
            String tokenUrl,
            String clientIdKey,
            String clientSecretKey,
            String scope,
            String accessTokenVar) {}

    /**
     * Everything auth adds to an import.
     *
     * @param suite how the suite declares requests are sent: what the document's {@code security} maps to
     * @param requests how each operation's request is sent, keyed by identity, one entry per operation
     * @param tokenRequests the setup requests, in the order their schemes are first used
     */
    record Plan(Auth suite, Map<Operation, Auth> requests, List<TokenRequest> tokenRequests) {}

    /**
     * Decides how the suite for {@code document} authenticates, mapping each security as the Auth part of
     * {@link OpenApiImporter#scaffold} describes.
     * <p>
     * The plan carries the decisions and the emitter writes them. A {@link Auth} carries the block, or for
     * an api key in the query the parameter and its secret; its {@code source} is the scheme's name, or
     * {@code security: []} for an empty security or a first alternative naming no scheme; its
     * {@code todo} says what the author must do — a scheme that is not imported, a token to obtain by
     * hand, a further scheme the spec requires as well. A document and an operation declaring no security
     * at all map to {@link Auth#UNDECLARED}; so does a document whose own {@code security} is empty, since
     * for the document that is the same as declaring none. Every key is already distinct: a scheme
     * needing a key an earlier one took carries {@code <scheme>.<key>}, and its token request asks for
     * the name {@code get a token for <scheme>}.
     *
     * @param document the document; must not be {@code null}
     * @return the plan; {@link Plan#suite()} is {@link Auth#UNDECLARED} if the document declares no
     *         {@code security}
     */
    static Plan plan(OpenApiDocument document) {
        var planning = new Planning(document);
        var suite = document.security().isEmpty() ? Auth.UNDECLARED : planning.auth(document.security());
        var requests = new IdentityHashMap<Operation, Auth>();
        for (var operation : document.operations()) {
            requests.put(operation, planning.auth(effective(document, operation)));
        }
        return new Plan(suite, requests, planning.tokenRequests());
    }

    /** The security that applies to {@code operation}: its own, else the document's; {@code null} if none. */
    private static List<Map<String, List<String>>> effective(OpenApiDocument document, Operation operation) {
        if (operation.security() != null) {
            return operation.security();
        }
        return document.security().isEmpty() ? null : document.security();
    }

    /** The first scheme of the first alternative, or {@code null} for none. */
    private static String chosen(List<Map<String, List<String>>> security) {
        if (security == null || security.isEmpty() || security.getFirst().isEmpty()) {
            return null;
        }
        return security.getFirst().keySet().iterator().next();
    }

    /** The keys one scheme uses, each already made distinct from every earlier scheme's. */
    private record Keys(Map<String, String> secrets, Map<String, String> env, Map<String, String> vars) {}

    /** One planning: the schemes used, their scopes and their keys. */
    private static final class Planning {

        private final OpenApiDocument document;
        private final Map<String, Keys> keys = new LinkedHashMap<>();
        private final Map<String, Set<String>> scopes = new LinkedHashMap<>();

        Planning(OpenApiDocument document) {
            this.document = document;
            var uses = new ArrayList<List<Map<String, List<String>>>>();
            uses.add(document.security());
            for (var operation : document.operations()) {
                uses.add(effective(document, operation));
            }
            var taken = new Keys(new HashMap<>(), new HashMap<>(), new HashMap<>());
            for (var use : uses) {
                var name = chosen(use);
                if (name == null) {
                    continue;
                }
                scopes.computeIfAbsent(name, key -> new LinkedHashSet<>())
                        .addAll(use.getFirst().get(name));
                if (!keys.containsKey(name)) {
                    keys.put(name, claim(name, taken));
                }
            }
        }

        /** Claims the keys {@code name}'s kind needs, putting its name in front of any already taken. */
        private Keys claim(String name, Keys taken) {
            var kind = kind(document.securitySchemes().get(name));
            var secrets =
                    switch (kind) {
                        case BEARER, OTHER_TOKEN -> List.of("token");
                        case BASIC -> List.of("username", "password");
                        case HEADER_KEY, QUERY_KEY -> List.of("apiKey");
                        case CLIENT_CREDENTIALS -> List.of("clientSecret");
                        case UNSUPPORTED -> List.<String>of();
                    };
            var clientCredentials = kind == Kind.CLIENT_CREDENTIALS;
            return new Keys(
                    claimAll(name, secrets, taken.secrets()),
                    claimAll(name, clientCredentials ? List.of("tokenUrl", "clientId") : List.of(), taken.env()),
                    claimAll(name, clientCredentials ? List.of("accessToken") : List.of(), taken.vars()));
        }

        private static Map<String, String> claimAll(String scheme, List<String> wanted, Map<String, String> taken) {
            var result = new LinkedHashMap<String, String>();
            for (var key : wanted) {
                var claimed = taken.containsKey(key) ? scheme + "." + key : key;
                taken.put(key, scheme);
                result.put(key, claimed);
            }
            return result;
        }

        List<TokenRequest> tokenRequests() {
            var result = new ArrayList<TokenRequest>();
            for (var entry : keys.entrySet()) {
                var scheme = document.securitySchemes().get(entry.getKey());
                if (kind(scheme) != Kind.CLIENT_CREDENTIALS) {
                    continue;
                }
                var keysOf = entry.getValue();
                result.add(new TokenRequest(
                        result.isEmpty() ? TOKEN_REQUEST : TOKEN_REQUEST + " for " + entry.getKey(),
                        entry.getKey(),
                        keysOf.env().get("tokenUrl"),
                        scheme.clientCredentialsTokenUrl(),
                        keysOf.env().get("clientId"),
                        keysOf.secrets().get("clientSecret"),
                        String.join(" ", scopes.get(entry.getKey())),
                        keysOf.vars().get("accessToken")));
            }
            return List.copyOf(result);
        }

        /** What {@code security} maps to; {@code null} is a security not declared at all. */
        Auth auth(List<Map<String, List<String>>> security) {
            if (security == null) {
                return Auth.UNDECLARED;
            }
            var name = chosen(security);
            if (name == null) {
                return new Auth(new Block(NONE, Map.of()), null, null, EMPTY_SECURITY, null);
            }
            var others = new ArrayList<>(security.getFirst().keySet());
            others.removeFirst();
            var also = others.isEmpty()
                    ? null
                    : "the spec also requires " + String.join(", ", others) + ", which the import does not send";
            var scheme = document.securitySchemes().get(name);
            var keysOf = keys.get(name);
            return switch (kind(scheme)) {
                case BEARER -> blockAuth(name, "bearer", Map.of("token", secret(keysOf, "token")), also);
                case BASIC -> {
                    var args = new LinkedHashMap<String, String>();
                    args.put("username", secret(keysOf, "username"));
                    args.put("password", secret(keysOf, "password"));
                    yield blockAuth(name, "basic", args, also);
                }
                case HEADER_KEY -> {
                    var args = new LinkedHashMap<String, String>();
                    args.put("key", secret(keysOf, "apiKey"));
                    args.put("header", scheme.name());
                    yield blockAuth(name, "apikey", args, also);
                }
                case QUERY_KEY -> new Auth(null, scheme.name(), keysOf.secrets().get("apiKey"), name, also);
                case CLIENT_CREDENTIALS ->
                    blockAuth(
                            name,
                            "bearer",
                            Map.of("token", "${vars." + keysOf.vars().get("accessToken") + "}"),
                            also);
                case OTHER_TOKEN ->
                    blockAuth(
                            name,
                            "bearer",
                            Map.of("token", secret(keysOf, "token")),
                            join(
                                    "obtain a token for " + name + " (" + scheme.type()
                                            + ") and provide it as the secret "
                                            + keysOf.secrets().get("token"),
                                    also));
                case UNSUPPORTED ->
                    new Auth(new Block(NONE, Map.of()), null, null, name, join(unsupported(name, scheme), also));
            };
        }

        private static Auth blockAuth(String name, String type, Map<String, String> args, String todo) {
            return new Auth(new Block(type, args), null, null, name, todo);
        }

        private static String secret(Keys keysOf, String key) {
            return "${secrets." + keysOf.secrets().get(key) + "}";
        }

        private static String unsupported(String name, SecurityScheme scheme) {
            if (scheme == null) {
                return "the spec does not define the scheme " + name + ", so it is not imported: set up its auth";
            }
            var kind = scheme.type()
                    + (scheme.scheme() != null ? " " + scheme.scheme() : "")
                    + (scheme.in() != null ? " in " + scheme.in() : "")
                    + ("apikey".equals(scheme.type()) && StringUtils.isBlank(scheme.name()) ? " without a name" : "");
            return "the scheme " + name + " (" + kind + ") is not imported: set up its auth";
        }

        private static String join(String first, String second) {
            return second == null ? first : first + "; " + second;
        }
    }

    /** What a scheme maps to. */
    private enum Kind {
        BEARER,
        BASIC,
        HEADER_KEY,
        QUERY_KEY,
        CLIENT_CREDENTIALS,
        OTHER_TOKEN,
        UNSUPPORTED
    }

    private static Kind kind(SecurityScheme scheme) {
        if (scheme == null) {
            return Kind.UNSUPPORTED;
        }
        return switch (scheme.type()) {
            case "http" ->
                switch (String.valueOf(scheme.scheme())) {
                    case "bearer" -> Kind.BEARER;
                    case "basic" -> Kind.BASIC;
                    default -> Kind.UNSUPPORTED;
                };
            case "apikey" ->
                StringUtils.isBlank(scheme.name())
                        ? Kind.UNSUPPORTED
                        : switch (String.valueOf(scheme.in())) {
                            case "header" -> Kind.HEADER_KEY;
                            case "query" -> Kind.QUERY_KEY;
                            default -> Kind.UNSUPPORTED;
                        };
            case "oauth2" -> scheme.clientCredentialsTokenUrl() != null ? Kind.CLIENT_CREDENTIALS : Kind.OTHER_TOKEN;
            case "openidconnect" -> Kind.OTHER_TOKEN;
            default -> Kind.UNSUPPORTED;
        };
    }

    /**
     * Whether {@code document} declares any security at all, in its own {@code security} or an
     * operation's.
     *
     * @param document the document; must not be {@code null}
     * @return {@code true} if a plan would hold anything but {@link Auth#UNDECLARED}
     */
    static boolean declaresSecurity(OpenApiDocument document) {
        if (!document.security().isEmpty()) {
            return true;
        }
        for (var operation : document.operations()) {
            if (operation.security() != null) {
                return true;
            }
        }
        return false;
    }
}
