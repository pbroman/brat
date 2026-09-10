package dev.pbroman.brat.core.loader;

/**
 * A YAML document in the shape Jackson binds, together with an index of where every value was written
 * — the value that passes between the two YAML engines the loader uses.
 * <p>
 * Produced by {@link YamlComposer}, which is where the reason for two engines is explained.
 *
 * @param root the converted document: a {@code Map<String, Object>} for a mapping document, a
 *        {@code List<Object>} for a sequence, or a scalar. Never {@code null}
 * @param positions where each value sat, keyed by JSON Pointer
 */
record YamlDocument(Object root, SourceIndex positions) {}
