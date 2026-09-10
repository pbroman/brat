package dev.pbroman.brat.core.loader;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Where every value in a document was written, keyed by its JSON Pointer.
 * <p>
 * Keyed by pointer ({@code /requests/0/name}) rather than by a dotted path deliberately: Jackson
 * renders its own binding-error paths this way, so an error can be looked up without translating
 * between two spellings — and translating is exactly where an index quietly stops matching.
 * <p>
 * Built while converting the composed node tree, because that is the only point at which both the
 * structure and the source marks are in hand: Jackson binds a synthesized in-memory tree and its own
 * reported locations refer to something the author never wrote.
 * <p>
 * Package-private: it exists to build error messages, and nothing outside the loader consumes it. A
 * UI mapping an error back to an editor location is the plausible future consumer, and is when to
 * widen it — not before.
 */
final class SourceIndex {

    private final Map<String, SourcePosition> positions;

    /**
     * Constructs an index over the positions gathered during conversion.
     *
     * @param positions position by JSON Pointer; copied, and never {@code null}
     */
    public SourceIndex(Map<String, SourcePosition> positions) {
        this.positions = positions == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(positions));
    }

    /**
     * The position of the value at {@code pointer}.
     *
     * @param pointer a JSON Pointer, e.g. {@code /requests/0/name}, or {@code null}
     * @return the position, or empty if the pointer is {@code null} or the document held nothing
     *         there — a caller must be able to report an error it cannot place, since a missing
     *         position is worth less than a missing error
     */
    public Optional<SourcePosition> at(String pointer) {
        return Optional.ofNullable(positions.get(pointer));
    }
}
