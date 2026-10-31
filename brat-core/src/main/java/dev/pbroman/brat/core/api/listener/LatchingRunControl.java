package dev.pbroman.brat.core.api.listener;

/**
 * The {@link RunControl} that {@link RunControl#create()} returns: two latches, safe to set and read
 * from any thread.
 */
final class LatchingRunControl implements RunControl {

    private volatile boolean stopping;
    private volatile boolean cancelled;

    @Override
    public void stop() {
        stopping = true;
    }

    @Override
    public void cancel() {
        // Stopping first: a reader that sees the cancel must also see the stop it implies.
        stopping = true;
        cancelled = true;
    }

    @Override
    public boolean isStopping() {
        return stopping;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
