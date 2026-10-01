package dev.pbroman.brat.core.api.reporting;

import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.exception.BratException;

/**
 * A named way of reporting a run: it creates, for each run, the listener that does the reporting.
 * <p>
 * The reporter itself is a stateless factory. One instance serves every run of a runner, and a
 * plugin's instance is shared by the whole process, so everything that belongs to one run — counts,
 * an open file, where to write — lives in the listener {@link #create} returns, never here.
 * <p>
 * A reporter is selected by its {@link #name()}. Register one with the runner's builder or declare
 * it in {@code META-INF/services/dev.pbroman.brat.core.api.reporting.RunReporter}; a later
 * registration with the same name replaces an earlier one.
 */
public interface RunReporter {

    /**
     * The name this reporter is selected by, such as {@code "console"}.
     * <p>
     * Matched exactly, case included. It must be the same on every call.
     *
     * @return the name; never {@code null} or blank
     */
    String name();

    /**
     * Creates the listener that reports one run.
     * <p>
     * <strong>Called once per run</strong>, and possibly concurrently for runs that overlap, so it
     * must not touch state shared between calls. Every call returns a fresh listener.
     * <p>
     * <strong>It validates and acquires nothing.</strong> Check {@code context.args()} here and reject
     * what is wrong, so a mistake fails before any request is sent; but open no file, socket or other
     * resource. The listener acquires what it needs when it receives {@code RunStarted} and releases
     * it on {@code RunFinished} — if {@code RunStarted} arrives, {@code RunFinished} will. A run can
     * fail before its first event, and a listener can be created and never used; either way it then
     * holds nothing.
     *
     * @param context the run-level configuration: this reporter's arguments and the renderer for
     *        interpolation outcomes; never {@code null}
     * @return a new listener for one run; never {@code null}
     * @throws BratException if {@code context.args()} holds a key this reporter does not know, lacks
     *         one it requires, or holds a value it cannot use
     */
    RunListener create(ReporterContext context);
}
