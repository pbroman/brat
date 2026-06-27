package dev.pbroman.brat.core.exception;

public class BratException extends RuntimeException {

    public BratException(String message) {
        super(message);
    }

    public BratException(String message, Throwable cause) {
        super(message, cause);
    }

}