package dev.pbroman.brat.core.data.result;

import static java.util.Objects.requireNonNull;

import jakarta.annotation.Nonnull;

import dev.pbroman.brat.core.data.Condition;

public record AssertionResult(Condition condition, String message, boolean passed) {

    public AssertionResult {
        requireNonNull(condition, "The condition cannot be null");
    }

    @Override
    @Nonnull
    public String toString() {
        var s = String.format("%s failed.", condition);
        if (message != null) {
            s += String.format(" Message: %s", message);
        }
        return s;
    }

}
