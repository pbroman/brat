package dev.pbroman.brat.core.data.result;

/**
 * Where one request execution sits: three coordinates the author wrote, and one the run assigned.
 * <p>
 * Carried both by a {@link RequestResult} and — before any result exists — by the events that fire
 * when a request starts and when an attempt finishes. Those two consumers are why it is a type rather
 * than four fields repeated in three places.
 * <p>
 * It describes a <em>request</em> and must not be generalised to suites: a suite has no {@code id}.
 *
 * @param path the address, built from the names from the root down and joined with {@code /}. What a
 *        report prints, an event carries, and {@code --select} matches. Computed, never authored
 * @param id the author's optional correlation key, or {@code null}. Survives a rename, which the
 *        path does not — baselines and trend history key on it and fall back to the path without one
 * @param name this node's own name, the last segment of the path
 * @param requestNo this execution's sequence number within the run
 */
public record RequestCoordinates(String path, String id, String name, int requestNo) {}
