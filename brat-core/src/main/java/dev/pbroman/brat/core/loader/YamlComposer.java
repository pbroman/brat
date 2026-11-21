package dev.pbroman.brat.core.loader;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/**
 * Turns YAML text into plain values — maps, lists, text, numbers, booleans and nulls — that Jackson
 * can bind, with anchors, aliases and merge keys resolved and the document read within
 * {@link YamlLimits}.
 * <p>
 * This is the half of the loader that knows snakeyaml exists, and it exists because Jackson cannot
 * read anchors, aliases or merge keys — and does not fail on them, it reads {@code alias: *base} as
 * the string {@code "base"} and a {@code <<:} merge key as a literal property. Those are the ordinary
 * way suites are written, so snakeyaml resolves them first and Jackson is handed a tree with nothing
 * left to resolve.
 * <p>
 * Two things come from converting the composed node tree here rather than letting snakeyaml construct
 * it. <strong>Positions</strong>: only the compose step keeps source marks, so an error can say which
 * line — Jackson binds a synthesized in-memory tree and its own reported locations point at something
 * the author never wrote. And the <strong>duplicate-key check</strong>, which snakeyaml performs in
 * the construct step a compose-only path skips, so this does it while inserting.
 */
public final class YamlComposer {

    private YamlComposer() {
        // utility class
    }

    /**
     * Composes {@code yaml} and converts it, indexing positions and rejecting duplicate keys.
     * <p>
     * Anchors, aliases and merge keys are already resolved by the compose step, so a {@code <<:}
     * merged mapping arrives flattened and an alias arrives as the value it points at. Merging then
     * overriding is therefore <em>not</em> a duplicate key: the merge is flattened with the explicit
     * key winning before this sees it, and the duplicate check runs on the result.
     * <p>
     * The document is read within {@code limits}, and a document that exceeds one is reported in
     * BRAT's words rather than as a raw engine failure — {@code <documentName> exceeds the <n> MB
     * limit}, {@code <documentName> uses more than the <n> permitted aliases} — since a large generated
     * document is a plausible accident and an unexplained parser error is not a useful thing to hand
     * its author. Recursive keys are never allowed.
     *
     * @param yaml the document content
     * @param limits the limits to read it within, and its name in messages; must not be {@code null}
     * @return the converted document and its position index
     * @throws BratException if the document is empty ({@code <documentName> is empty}); if it is not
     *         valid YAML ({@code <documentName> is not valid YAML: <the engine's reason> at line L, column
     *         C} — the reason only, never a quoted part of the document, which may hold secrets); if one
     *         mapping holds the same key twice, naming the key and where the second one sat; or if it
     *         exceeds a limit, naming which limit and its value
     */
    static YamlDocument compose(String yaml, YamlLimits limits) {
        var node = composeNode(yaml, limits);
        var positions = new LinkedHashMap<String, SourcePosition>();
        var root = convert(node, "", positions);
        return new YamlDocument(root, new SourceIndex(positions));
    }

    /**
     * Composes {@code yaml} and converts it, as {@link #compose(String, YamlLimits)} does, returning
     * only the value: for a reader that needs the document but not where each part of it sat.
     *
     * @param yaml the document content
     * @param limits the limits to read it within, and its name in messages; must not be {@code null}
     * @return the document's root value: a {@code Map<String, Object>} for a mapping, a {@code List}
     *         for a list, or the scalar; {@code null} only for a document that is a lone null
     * @throws BratException in every case {@link #compose(String, YamlLimits)} does
     */
    public static Object tree(String yaml, YamlLimits limits) {
        return compose(yaml, limits).root();
    }

    /**
     * Composes the text, translating the engine's own failures into {@link BratException}.
     *
     * @param yaml the document content
     * @param limits the limits to read it within
     * @return the composed root node
     */
    private static Node composeNode(String yaml, YamlLimits limits) {
        Require.nonNull(limits, "YAML limits must not be null");
        Require.nonBlank(yaml, limits.documentName() + " is empty");
        try {
            // Duplicate keys already rejected, marks kept, no recursive keys, the two limits set
            // explicitly so the numbers in a message are BRAT's. Global tags stay off - YAML 1.2.
            //
            // The schema is NOT the default, and this is the line that makes `<<: *anchor` work. Only
            // CoreSchema's resolver maps `<<` to Tag.MERGE, and the composer flattens a merge only when
            // that tag is present - under the default schema a merge key survives as a literal `<<`
            // property and binding fails on it. Core is still YAML 1.2, so `no` stays a string: its
            // BOOL is true|True|TRUE|false|False|FALSE, with no yes/no/on/off.
            return new Compose(LoadSettings.builder()
                            .setSchema(new CoreSchema())
                            .setCodePointLimit(limits.maxMegabytes() * 1024 * 1024)
                            .setMaxAliasesForCollections(limits.maxAliases())
                            .build())
                    .composeString(yaml)
                    .orElseThrow(() -> new BratException(limits.documentName() + " is empty"));
        } catch (YamlEngineException e) {
            throw new BratException(limitAware(e, limits), e);
        }
    }

    /**
     * Restates an engine failure in BRAT's own words where it is a limit rather than a syntax error.
     *
     * @param failure the engine's failure
     * @param limits the limits the document was read within
     * @return what to report
     */
    private static String limitAware(YamlEngineException failure, YamlLimits limits) {
        var text = reasonOf(failure);
        // "The incoming YAML document exceeds the limit: 3145728 code points." — matched on the engine's
        // own wording, so a test asserting BRAT's words is what keeps this honest if the wording moves.
        if (text.contains("code points")) {
            return limits.documentName() + " exceeds the " + limits.maxMegabytes() + " MB limit: " + text;
        }
        if (text.contains("aliases for non-scalar")) {
            return limits.documentName() + " uses more than the " + limits.maxAliases() + " permitted aliases: " + text;
        }
        return limits.documentName() + " is not valid YAML: " + text;
    }

    /**
     * The engine's reason for a failure, without the excerpt of the document a marked failure quotes
     * beneath it.
     * <ul>
     *   <li>a failure with a mark and a problem: the problem, then {@code " at line L, column C"} where
     *       the problem has a mark;</li>
     *   <li>a failure with a mark and no problem: its context, positioned the same way by the context's
     *       mark; {@code "no reason given"} when it has no context either;</li>
     *   <li>a failure without a mark, which quotes nothing: its message, or the empty string when it
     *       has none.</li>
     * </ul>
     * Whatever the failure, the result never holds any of the document's text.
     *
     * @param failure the engine's failure; must not be {@code null}
     * @return the reason; never {@code null}
     */
    static String reasonOf(YamlEngineException failure) {
        if (failure instanceof MarkedYamlEngineException marked) {
            if (marked.getProblem() != null) {
                return marked.getProblem().strip() + positioned(marked.getProblemMark());
            }
            if (marked.getContext() != null) {
                return marked.getContext().strip() + positioned(marked.getContextMark());
            }
            return "no reason given";
        }
        return failure.getMessage() == null ? "" : failure.getMessage();
    }

    private static String positioned(Optional<Mark> mark) {
        return mark.map(m -> " at " + positionOf(m)).orElse("");
    }

    /**
     * Converts one node, recording where it sat and recursing into its children.
     *
     * @param node the node to convert
     * @param pointer this node's JSON Pointer
     * @param positions the index being built, added to
     * @return the plain value
     */
    private static Object convert(Node node, String pointer, Map<String, SourcePosition> positions) {
        node.getStartMark().map(YamlComposer::positionOf).ifPresent(p -> positions.put(pointer, p));
        return switch (node) {
            case MappingNode mapping -> convertMapping(mapping, pointer, positions);
            case SequenceNode sequence -> convertSequence(sequence, pointer, positions);
            case ScalarNode scalar -> convertScalar(scalar);
            // Unreachable: composing yields only these three, and the fourth concrete Node type
            // (AnchorNode) is built solely by the representer, which is the dumping side. The arm
            // exists because Node is not sealed, so the switch needs one.
            default -> throw new BratException("Unsupported YAML node type " + node.getNodeType());
        };
    }

    /**
     * Converts a mapping, rejecting a key written twice.
     *
     * @param mapping the mapping node
     * @param pointer the mapping's JSON Pointer
     * @param positions the index being built
     * @return a map of converted entries, in document order
     */
    private static Map<String, Object> convertMapping(
            MappingNode mapping, String pointer, Map<String, SourcePosition> positions) {
        var result = new LinkedHashMap<String, Object>();
        for (NodeTuple tuple : mapping.getValue()) {
            var key = String.valueOf(convert(tuple.getKeyNode(), pointer, positions));
            if (result.containsKey(key)) {
                throw new BratException(String.format(
                        "Duplicate key '%s'%s. A mapping may hold each key once", key, at(tuple.getKeyNode())));
            }
            var entryPointer = pointer + "/" + escape(key);
            result.put(key, convert(tuple.getValueNode(), entryPointer, positions));
            // An entry begins at its key, and that is where an author looks when told a key is
            // unknown, duplicated or missing. Recorded after the recursion so it wins over the value's
            // own position, which for a nested block is the line below.
            tuple.getKeyNode()
                    .getStartMark()
                    .map(YamlComposer::positionOf)
                    .ifPresent(position -> positions.put(entryPointer, position));
        }
        return result;
    }

    /**
     * Converts a sequence.
     *
     * @param sequence the sequence node
     * @param pointer the sequence's JSON Pointer
     * @param positions the index being built
     * @return a list of converted entries
     */
    private static List<Object> convertSequence(
            SequenceNode sequence, String pointer, Map<String, SourcePosition> positions) {
        var result = new ArrayList<>();
        var items = sequence.getValue();
        for (var i = 0; i < items.size(); i++) {
            result.add(convert(items.get(i), pointer + "/" + i, positions));
        }
        return result;
    }

    /**
     * Converts a scalar using the tag the resolver assigned it, so a value binds as the type it was
     * written as — the same view Jackson's own YAML parser would have produced.
     *
     * @param scalar the scalar node
     * @return the typed value, or {@code null}
     */
    private static Object convertScalar(ScalarNode scalar) {
        var text = scalar.getValue();
        var tag = scalar.getTag();
        if (Tag.NULL.equals(tag)) {
            return null;
        }
        if (Tag.BOOL.equals(tag)) {
            return Boolean.parseBoolean(text);
        }
        if (Tag.INT.equals(tag) || Tag.FLOAT.equals(tag)) {
            // BigDecimal rather than Long/Double for the same reason the number rule uses it: every
            // value reaches BRAT as text, so there is no precision to gain and some to lose.
            return new BigDecimal(text);
        }
        return text;
    }

    /**
     * Converts a mark to a 1-based position, since the engine counts from zero and authors do not.
     *
     * @param mark the engine's mark
     * @return the position
     */
    private static SourcePosition positionOf(Mark mark) {
        return new SourcePosition(mark.getLine() + 1, mark.getColumn() + 1);
    }

    /**
     * Renders a node's position for an error message.
     *
     * @param node the node
     * @return {@code " at line L, column C"}, or the empty string when the engine kept no mark
     */
    private static String at(Node node) {
        return node.getStartMark().map(mark -> " at " + positionOf(mark)).orElse("");
    }

    /**
     * Escapes a key for use in a JSON Pointer, per RFC 6901.
     *
     * @param key the mapping key
     * @return the escaped key
     */
    private static String escape(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }
}
