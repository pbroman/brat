package dev.pbroman.brat.core.exception;

import dev.pbroman.brat.core.data.result.ValidationType;

public class ValidationException extends Exception {

    private ValidationType validationType;

    public ValidationException(String message, ValidationType type) {
        super(message);
        this.validationType = type;
    }

    public ValidationException(String message, ValidationType type, Throwable cause) {
        super(message, cause);
        this.validationType = type;
    }

    public ValidationType getValidationType() {
        return validationType;
    }

}
