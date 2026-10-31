package dev.pbroman.brat.cli;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import dev.pbroman.brat.core.api.listener.RunControl;
import sun.misc.Signal;
import sun.misc.SignalHandler;

/**
 * Stops a run on Ctrl-C, one level further with each press.
 * <ol>
 *   <li>The first {@linkplain RunControl#stop() stops} the run gracefully: nothing new starts, and open
 *       suites run their teardown.</li>
 *   <li>The second {@linkplain RunControl#cancel() cancels} it: nothing more starts, teardown
 *       included.</li>
 *   <li>The third exits at once with {@code 1}, for a run stuck where no check is reached.</li>
 * </ol>
 * Each press prints one line to stderr saying what it did and what the next press would do.
 */
final class InterruptStopper implements AutoCloseable {

    private static final Signal INTERRUPT = new Signal("INT");

    private final RunControl control;
    private final PrintWriter err;
    private final IntConsumer exit;
    private final AtomicInteger presses = new AtomicInteger();

    /** What handled {@code SIGINT} before this was installed; {@code null} while not installed. */
    private SignalHandler previous;

    /**
     * Constructs a stopper for one run, not yet installed on any signal.
     *
     * @param control the run's control; must not be {@code null}
     * @param err where each press is reported; must not be {@code null}
     * @param exit ends the process with the given status, on the third press; must not be {@code null}
     */
    InterruptStopper(RunControl control, PrintWriter err, IntConsumer exit) {
        this.control = control;
        this.err = err;
        this.exit = exit;
    }

    /**
     * Creates a stopper for {@code control} and installs it on {@code SIGINT}, until it is
     * {@linkplain #close() closed}.
     *
     * @param control the run's control; must not be {@code null}
     * @param err where each press is reported; must not be {@code null}
     * @return the installed stopper; never {@code null}
     */
    static InterruptStopper install(RunControl control, PrintWriter err) {
        var stopper = new InterruptStopper(control, err, System::exit);
        stopper.previous = Signal.handle(INTERRUPT, signal -> stopper.interrupted());
        return stopper;
    }

    /**
     * Responds to one Ctrl-C: the first stops the run, the second cancels it, every later one exits
     * with {@code 1}. Each prints its line to stderr.
     */
    void interrupted() {
        switch (presses.incrementAndGet()) {
            case 1 -> {
                control.stop();
                report("stopping: no new request starts, and open suites run their teardown."
                        + " Ctrl-C again to stop at once");
            }
            case 2 -> {
                control.cancel();
                report("stopping at once, teardown included. Ctrl-C again to exit");
            }
            default -> {
                report("exiting");
                exit.accept(1);
            }
        }
    }

    /**
     * Prints one line to stderr and flushes it, since the run's own output keeps going around it.
     *
     * @param message what the press did
     */
    private void report(String message) {
        err.println("brat: " + message);
        err.flush();
    }

    /**
     * Puts back whatever handled {@code SIGINT} before {@link #install} — the JVM's own, which ends the
     * process. Does nothing for a stopper that was never installed, or is already closed.
     */
    @Override
    public void close() {
        if (previous != null) {
            Signal.handle(INTERRUPT, previous);
            previous = null;
        }
    }
}
