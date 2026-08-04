package dev.pbroman.brat.core.exception;

/**
 * The single exception type of {@code brat-core}: unchecked, and thrown for every failure the
 * library reports.
 */
public class BratException extends RuntimeException {

    /**
     * Constructs an exception with a message and no cause, which is the usual form — a cause is
     * chained only where it cannot carry content that must not be logged.
     *
     * @param message the message
     */
    public BratException(String message) {
        super(message);
    }

    /**
     * Constructs an exception wrapping a cause.
     *
     * @param message the message
     * @param cause the underlying failure
     */
    public BratException(String message, Throwable cause) {
        super(message, cause);
    }
}
