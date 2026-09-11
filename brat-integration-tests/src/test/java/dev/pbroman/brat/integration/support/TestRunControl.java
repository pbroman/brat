package dev.pbroman.brat.integration.support;

import java.util.concurrent.atomic.AtomicBoolean;

import dev.pbroman.brat.core.api.listener.RunControl;

/**
 * A control a test can both pass in and pull the trigger on.
 *
 * <p>Serves both needs: left alone it is the control of a run nothing stops, and {@link #cancel()} is
 * what a listener calls to prove the run unwinds between requests.
 */
public final class TestRunControl implements RunControl {

    private final AtomicBoolean cancelled = new AtomicBoolean();

    @Override
    public void cancel() {
        cancelled.set(true);
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
