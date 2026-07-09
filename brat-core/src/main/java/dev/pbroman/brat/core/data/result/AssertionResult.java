package dev.pbroman.brat.core.data.result;

import dev.pbroman.brat.core.data.Condition;

import static dev.pbroman.brat.core.util.ExceptionUtil.bratExceptionOnNull;

public record AssertionResult(Condition condition, String message, boolean passed) {

    public AssertionResult {
        bratExceptionOnNull(condition, "The condition cannot be null");
    }

    @Override
    public String toString() {
        var s = String.format("%s failed.", condition);
        if (message != null) {
            s += String.format(" Message: %s", message);
        }
        return s;
    }

}
