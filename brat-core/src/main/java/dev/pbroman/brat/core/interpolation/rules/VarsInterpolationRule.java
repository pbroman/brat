package dev.pbroman.brat.core.interpolation.rules;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.data.runtime.CaptureTombstone;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.interpolation.InterpolationChecks.requireNamespaces;
import static dev.pbroman.brat.core.util.Constants.VARS;

/**
 * An {@link InterpolationRule} for variables.
 */
@Slf4j
public final class VarsInterpolationRule extends AbstractInterpolationRule {

    /**
     * Constructs an {@link InterpolationRule} for variables.
     *
     */
    public VarsInterpolationRule() {
        super(VARS);
    }

    @Override
    public String resolve(String input, RuntimeData runtimeData) {
        requireNamespaces(runtimeData, VARS);
        return simpleInterpolation(input, runtimeData, runtimeData.getVars());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Two policies, and which one applies is the whole point of the distinction:
     * <ul>
     *   <li><strong>A variable that was never set</strong> logs a warning and resolves to the empty
     *       string. {@code vars} is deliberately the one soft-failing namespace.</li>
     *   <li><strong>A variable whose capture failed</strong> — one carrying a {@link CaptureTombstone} —
     *       throws, naming the cause and the request whose capture failed. Soft-failing here is what
     *       turns a broken capture into a 404 several requests later that names nothing about it. A
     *       tombstone with no path reads {@code unknown} rather than {@code null}.</li>
     * </ul>
     * An authored {@code :-} fallback wins over both, because it is spent before this runs.
     *
     * @throws BratException if a capture of {@code placeholder} failed earlier in this run
     */
    @Override
    protected String onMissingReplacement(String placeholder, String input, RuntimeData runtimeData) {
        var tombstone = runtimeData.getTombstone(placeholder);
        if (tombstone != null) {
            throw new BratException(String.format(
                    "The var '%s' could not be set at request '%s'. %s",
                    placeholder, tombstone.path() != null ? tombstone.path() : "unknown", tombstone.message()));
        }
        log.warn("The variable '{}' has not been set, replacing it with an empty string", placeholder);
        return "";
    }
}
