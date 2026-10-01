package dev.pbroman.brat.core.reporting;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.reporting.RunReporter;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

import static dev.pbroman.brat.core.util.Require.nonBlank;
import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * The run reporters a runner can select, keyed by {@link RunReporter#name()}.
 */
@Slf4j
public final class RunReporterRegistry {

    private final Map<String, RunReporter> reporters;

    /**
     * Constructs a registry over the reporters a runner has available.
     * <p>
     * The order of {@code reporters} matters only for repeated names: a reporter whose name repeats
     * an earlier one <strong>replaces</strong> it, and the replacement is logged at WARN naming both.
     * That is how a registration overrides a core reporter, so the caller puts core's first.
     *
     * @param reporters the reporters, in registration order; may be empty. Not retained — a later
     *        change to the collection does not affect the registry
     * @throws BratException if {@code reporters} is {@code null}, holds a {@code null} element, or
     *         holds a reporter whose {@link RunReporter#name()} is {@code null} or blank
     */
    public RunReporterRegistry(Collection<RunReporter> reporters) {
        nonNull(reporters, "The reporters of a registry must not be null");
        var byName = new LinkedHashMap<String, RunReporter>();
        for (var reporter : reporters) {
            nonNull(reporter, "A reporter of a registry must not be null");
            var name = reporter.name();
            nonBlank(
                    name,
                    "A reporter's name must not be null or blank: "
                            + reporter.getClass().getName());
            var replaced = byName.put(name, reporter);
            if (replaced != null) {
                log.warn(
                        "Two reporters are named '{}': {} replaces {}. The later one wins",
                        name,
                        reporter.getClass().getName(),
                        replaced.getClass().getName());
            }
        }
        this.reporters = Collections.unmodifiableMap(byName);
    }

    /**
     * Looks up a reporter by name.
     *
     * @param name the reporter's name, matched exactly; must not be {@code null}
     * @return the reporter registered under {@code name}
     * @throws BratException if {@code name} is {@code null}, or if no reporter is registered under it —
     *         the message names every registered reporter
     */
    public RunReporter get(String name) {
        Require.nonNull(name, "The reporter name must not be null");
        if (!reporters.containsKey(name)) {
            throw new BratException(
                    String.format("There is no reporter with name '%s', registered: %s", name, reporters.keySet()));
        }
        return reporters.get(name);
    }
}
