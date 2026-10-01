package dev.pbroman.brat.core.api.reporting;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import dev.pbroman.brat.core.api.rendering.OutcomeRenderer;
import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * What a {@link RunReporter} is given to create the listener for one run.
 *
 * @param args this reporter's arguments for the run, such as {@code detail=all}; never {@code null},
 *        empty when none were given, unmodifiable
 * @param renderer renders interpolation outcomes with secrets masked; never {@code null}
 */
public record ReporterContext(Map<String, String> args, OutcomeRenderer renderer) {

    /**
     * Validates both components and copies {@code args}, so that the context cannot change after it
     * was handed to a reporter.
     *
     * @throws BratException if {@code args} or {@code renderer} is {@code null}
     */
    public ReporterContext {
        nonNull(args, "The args of a ReporterContext must not be null");
        nonNull(renderer, "The renderer of a ReporterContext must not be null");
        args = Collections.unmodifiableMap(new LinkedHashMap<>(args));
    }
}
