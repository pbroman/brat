package dev.pbroman.brat.core.data.result;

import dev.pbroman.brat.core.data.Condition;

public record AssertionFail(Condition condition, String message) {

    public String toString() {
        var s = String.format("%s failed.", condition);
        if (message != null) {
            s += String.format(" Message: %s", message);
        }
        return s;
    }

}
