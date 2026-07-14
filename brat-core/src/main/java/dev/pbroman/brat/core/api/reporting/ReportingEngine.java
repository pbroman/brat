package dev.pbroman.brat.core.api.reporting;

import java.util.Map;

import dev.pbroman.brat.core.api.interpolation.InterpolationOutcome;
import dev.pbroman.brat.core.exception.BratException;

/**
 * Produces a rendered report from a set of named interpolation outcomes.
 */
public interface ReportingEngine {

    /**
     * Produces a report of {@code outcomes} in the shape identified by {@code kind}.
     *
     * @param kind the report kind (e.g. {@code "console"}, {@code "verbose-cli"}, {@code "log"})
     * @param outcomes the named outcomes to report on, in field order
     * @return the produced report; never {@code null}
     * @throws BratException if {@code kind} is {@code null}, or no rule recognizes it
     */
    String report(String kind, Map<String, InterpolationOutcome> outcomes);
}
