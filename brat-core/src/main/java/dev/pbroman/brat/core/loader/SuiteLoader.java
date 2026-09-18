package dev.pbroman.brat.core.loader;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;
import dev.pbroman.brat.core.util.ResourceReader;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import static dev.pbroman.brat.core.util.Constants.HTTP;

/**
 * Turns a suite document into a {@link TestSuite}, or says why it cannot in words its author can act
 * on.
 * <p>
 * The line this draws, and the one the rest of the design draws with it: <strong>the loader
 * guarantees structure, execution consumes content</strong>. Everything about the shape of the
 * document is settled here, before any request runs; nothing about the <em>values</em> is, because a
 * value may hold {@code ${...}} and is not knowable until the request that uses it.
 * <p>
 * Content is the primary input. A future UI editing a suite in memory has no file to point at, so
 * {@link #load(String, String)} is the real entry point and anything file-shaped is a wrapper over it.
 * <p>
 * Four steps, and the first two are {@link YamlComposer}'s — Jackson cannot resolve
 * anchors and does not complain about them, so snakeyaml resolves them before Jackson sees anything:
 * <ol>
 *   <li><strong>compose</strong> the text with snakeyaml, keeping the source marks;</li>
 *   <li><strong>convert</strong> that node tree into plain maps and lists, indexing where each value
 *       sat and rejecting a key written twice — both of which are only possible here;</li>
 *   <li><strong>bind</strong> the tree to {@link TestSuite} with Jackson, rejecting a key it does not
 *       know and a value of the wrong shape;</li>
 *   <li><strong>check addressability</strong> — names, uniqueness, and the rules a path depends on.</li>
 * </ol>
 * An error from any step names its key and its position, the position coming from the index built in
 * step 2.
 */
public final class SuiteLoader {

    private final ObjectMapper mapper;

    /**
     * Constructs a loader for documents that may declare any of the given protocols.
     * <p>
     * <strong>The map is the wiring's, not the loader's.</strong> It comes from the registered
     * request handlers — each declaring the class its protocol's blocks bind to — which is what makes
     * "you can author exactly what you can execute" true by construction. {@code Brat.loader()} is the
     * path that guarantees it; building one by hand is for a consumer with its own protocol set, and
     * that consumer owns keeping the two in step.
     *
     * @param protocols the definition class per protocol name; never {@code null}, and a copy is kept
     * @throws BratException if {@code protocols} is {@code null}
     */
    public SuiteLoader(Map<String, Class<? extends RequestDefinition>> protocols) {
        Require.nonNull(protocols, "The protocols a loader can bind must not be null");
        // Unknown keys fail rather than binding nothing, and enum values match whatever case an
        // author wrote - the published documents use both.
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .addModule(new SimpleModule()
                        .addDeserializer(
                                RequestDefinition.class, new RequestDefinitionDeserializer(Map.copyOf(protocols))))
                .build();
    }

    /**
     * A loader for documents that use HTTP and nothing else.
     * <p>
     * Named rather than offered as a no-argument constructor, so that a loader knowing one protocol
     * says so at the call site. A suite it loads that declares any other protocol fails, naming
     * {@code http} as all it knows — which is the right answer for a runner wired the same way, and a
     * misleading one for a runner that has more. Prefer {@code Brat.loader()} wherever a runner exists.
     *
     * @return a loader that binds every {@code requestDefinition:} as HTTP
     */
    public static SuiteLoader httpOnly() {
        return new SuiteLoader(Map.of(HTTP, HttpRequestDefinition.class));
    }

    /**
     * Loads a suite from document content.
     *
     * @param yaml the document content
     * @param origin what to call this document in an error — a file path, or something like
     *        {@code <string>} for content with no file behind it. May be {@code null}, in which case
     *        errors carry a position but no name
     * @return the bound suite, guaranteed <em>addressable</em>: every node has a name, sibling names
     *         are unique, no name holds a {@code /}, and no two requests share an authored {@code id}
     * @throws BratException for anything that makes the document unusable, each naming the offending
     *         key and where it sat — see {@link YamlComposer} for what the document itself must be,
     *         and {@link SuiteChecks} for the rules a bound suite must satisfy. A value of the wrong
     *         shape for its field is rejected here, between the two
     */
    public TestSuite load(String yaml, String origin) {
        var document = YamlComposer.compose(yaml);
        if (!(document.root() instanceof Map)) {
            throw LoaderErrors.at(origin, document, "", "A suite document must be a mapping");
        }
        var suite = bind(document, origin);
        SuiteChecks.check(suite, document, origin);
        return suite;
    }

    /**
     * Binds the converted tree, turning a binding failure into a positioned message.
     *
     * @param document the converted document
     * @param origin what to call it in an error
     * @return the bound suite
     */
    private TestSuite bind(YamlDocument document, String origin) {
        try {
            return mapper.convertValue(document.root(), TestSuite.class);
        } catch (UnrecognizedPropertyException e) {
            throw LoaderErrors.at(origin, document, pointerOf(e), "Unknown key '" + e.getPropertyName() + "'");
        } catch (JacksonException e) {
            var pointer = pointerOf(e);
            throw LoaderErrors.at(origin, document, pointer, describe(e, pointer));
        }
    }

    /**
     * Builds a JSON Pointer from the path Jackson reports, which is why the index is keyed that way.
     * <p>
     * The last reference is the failing property itself, so nothing is appended — Jackson names it
     * both in {@code getPropertyName()} and at the end of the path, and appending would produce a
     * pointer the index cannot hold.
     *
     * @param e the binding failure
     * @return the pointer
     */
    private static String pointerOf(JacksonException e) {
        var pointer = new StringBuilder();
        for (var reference : e.getPath()) {
            if (reference.getPropertyName() != null) {
                pointer.append('/')
                        .append(reference.getPropertyName().replace("~", "~0").replace("/", "~1"));
            } else if (reference.getIndex() >= 0) {
                pointer.append('/').append(reference.getIndex());
            }
        }
        return pointer.toString();
    }

    /**
     * Says what is wrong with a bound value in words that name the key and not the value.
     * <p>
     * Jackson's own message cannot be passed through: it quotes the offending value back
     * ({@code from String "s3cr3t-token"}), which is exactly what {@link LoaderErrors} forbids. A
     * data type that rejects its own arguments has already said it in BRAT's words, so that message
     * is used as it stands.
     *
     * @param e the binding failure
     * @param pointer the JSON Pointer of the offending value
     * @return the message to report
     */
    private static String describe(JacksonException e, String pointer) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof BratException) {
            return cause.getMessage();
        }
        // The pointer is never empty here: load() has already established that the root is a mapping,
        // so a binding failure is always inside one.
        var key =
                StringUtils.substringAfterLast(pointer, "/").replace("~1", "/").replace("~0", "~");
        var target = e instanceof MismatchedInputException mismatch ? mismatch.getTargetType() : null;
        if (target != null && target.isEnum()) {
            return "Value for '" + key + "' is not one of " + Arrays.toString(target.getEnumConstants());
        }
        return target == null
                ? "Value for '" + key + "' cannot be bound to its field"
                : "Value for '" + key + "' is not a " + target.getSimpleName();
    }

    /**
     * Equivalent to {@link #load(String, String)} with no origin.
     *
     * @param yaml the document content
     * @return the bound suite
     */
    public TestSuite load(String yaml) {
        return load(yaml, null);
    }

    /**
     * Reads a file and loads it, using the path as the origin.
     * <p>
     * ⚠ <strong>Temporary.</strong> It is I/O on a type whose job is parsing in-memory content, which
     * the naming convention separates deliberately. It exists so a caller can point at a file before
     * anything else can read one; it belongs to whatever ends up owning file access, and goes from
     * here when that exists.
     *
     * @param path the file to read
     * @return the bound suite
     * @throws BratException if the file cannot be read, or for any reason
     *         {@link #load(String, String)} throws
     */
    public TestSuite load(Path path) {
        return load(ResourceReader.readFileToString("file:" + path), String.valueOf(path));
    }
}
