package dev.pbroman.brat.core.secrets;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.exception.BratException;
import org.apache.commons.lang3.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static dev.pbroman.brat.core.util.ExceptionUtils.bratExceptionOnNull;
import static dev.pbroman.brat.core.util.JacksonUtils.locationOf;

/**
 * Reads a YAML document into a flat map of dotted keys to their values as text.
 * <p>
 * Nested mappings are flattened by joining the path with dots, to any depth, so
 * {@code db: {password: x}} is read as the single entry {@code db.password -> x}. Every scalar is
 * returned exactly as written in the source, with no YAML type inference applied: {@code 1.50}
 * stays {@code "1.50"} rather than becoming {@code "1.5"}, {@code 0123} keeps its leading zero, and
 * {@code true} is the string {@code "true"} rather than a boolean. This makes the document a
 * namespace of opaque string values, which is what both secret values and secret locators need.
 * <p>
 * Anchors and aliases are <strong>not supported</strong> and the result of a document using them is
 * undefined — including silently wrong. Documents read by this loader are flat data, not templates.
 * <p>
 * Failures never quote the document. A {@link BratException} from here names the key or path at
 * fault, and for a parse error the line and column, but never the source text around it — the
 * documents this reads hold secrets, and an exception message ends up in logs and reports.
 */
public final class FlatYamlLoader {

    private static final YAMLMapper MAPPER = YAMLMapper.builder().build();

    private FlatYamlLoader() {
        // utility class
    }

    /**
     * Reads {@code yaml} into a flat map of dotted keys to text values.
     *
     * @param yaml the YAML document content; a document that is empty, blank, or comments only
     *        yields an empty map, as does a mapping whose only entries are empty nested mappings
     * @return an unmodifiable map of dotted key to the value's source text, in document order;
     *         a value may be the empty string, which is a real value rather than an absent one
     * @throws BratException if {@code yaml} is {@code null}; if it is not well-formed YAML; if its
     *         top level is not a mapping; if any value anywhere in it is a sequence or any other
     *         non-scalar; if any value is null — whether written {@code null} or left empty after
     *         the colon; if any key is blank; if the same key appears twice in one mapping; or if
     *         flattening makes a nested path collide with a literal dotted key, as
     *         {@code db: {password: x}} does with {@code "db.password": y} in the same document
     */
    public static Map<String, String> load(String yaml) {
        bratExceptionOnNull(yaml, "Cannot read a null YAML document");

        var flattened = new LinkedHashMap<String, String>();
        try (JsonParser parser = MAPPER.createParser(yaml)) {
            var firstToken = parser.nextToken();
            if (firstToken == null) {
                return Map.of();
            }
            if (firstToken != JsonToken.START_OBJECT) {
                throw new BratException("The top level of a YAML document must be a mapping, but was " + firstToken);
            }
            readMapping(parser, "", flattened);
        } catch (JacksonException e) {
            throw new BratException("Could not parse the YAML document" + locationOf(e)
                    + "; the parser's own message is omitted because it quotes the source, which may be a secret");
        }
        return Collections.unmodifiableMap(flattened);
    }

    private static void readMapping(JsonParser parser, String prefix, Map<String, String> flattened) {
        var keysInThisMapping = new HashSet<String>();
        for (var token = parser.nextToken(); token != JsonToken.END_OBJECT; token = parser.nextToken()) {
            if (token != JsonToken.PROPERTY_NAME) {
                throw new BratException("Expected a YAML mapping key, but found " + token);
            }
            var key = parser.currentName();
            if (StringUtils.isBlank(key)) {
                throw new BratException("Blank YAML mapping key under '" + prefix + "'");
            }
            if (!keysInThisMapping.add(key)) {
                throw new BratException("Duplicate key '" + key + "' in the same mapping");
            }
            var path = prefix.isEmpty() ? key : prefix + "." + key;

            switch (parser.nextToken()) {
                case START_OBJECT -> readMapping(parser, path, flattened);
                case START_ARRAY -> throw new BratException("Sequences are not supported, but '" + path + "' is one");
                case VALUE_NULL -> throw new BratException("Null value for '" + path + "'");
                case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT, VALUE_TRUE, VALUE_FALSE ->
                        put(path, parser.getString(), flattened);
                case null, default -> throw new BratException("Unsupported value for '" + path + "'");
            }
        }
    }

    private static void put(String path, String value, Map<String, String> flattened) {
        if (flattened.put(path, value) != null) {
            throw new BratException("Flattening produces the key '" + path + "' twice, "
                    + "so a nested mapping collides with a literal dotted key");
        }
    }

}
