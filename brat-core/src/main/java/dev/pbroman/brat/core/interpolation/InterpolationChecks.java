package dev.pbroman.brat.core.interpolation;

import java.util.ArrayList;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import org.apache.commons.lang3.StringUtils;

/**
 * Argument guards for interpolation: the runtime data a rule resolves against must exist, and so
 * must the namespaces that rule reads from it.
 */
public final class InterpolationChecks {

    private InterpolationChecks() {
        // no instances
    }

    /**
     * Requires that {@code runtimeData} exists and holds every namespace in {@code namespaces}.
     * Naming no namespace checks only that {@code runtimeData} itself is present, which is what
     * the rules resolving against something other than a {@link RuntimeData} namespace need.
     *
     * @param runtimeData the runtime data to check
     * @param namespaces the namespace keys that must be present, e.g. {@code constants}
     * @throws IllegalArgumentException if {@code runtimeData} is {@code null}, or if any named
     *         namespace is absent from it; the message names every problem found, not only the
     *         first
     */
    public static void requireNamespaces(RuntimeData runtimeData, String... namespaces) {
        if (runtimeData == null) {
            throw new IllegalArgumentException(" The runtime data for the interpolation is null");
        }
        var missing = new ArrayList<String>();
        for (var namespace : namespaces) {
            if (runtimeData.getData(namespace) == null) {
                missing.add(" The '" + namespace + "' runtime data for the interpolation is null");
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(StringUtils.join(missing, ','));
        }
    }
}
