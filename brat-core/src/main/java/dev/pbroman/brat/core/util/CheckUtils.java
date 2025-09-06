package dev.pbroman.brat.core.util;

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.logging.log4j.util.Strings;

import dev.pbroman.brat.core.data.runtime.RuntimeData;
import io.micrometer.common.util.StringUtils;

public class CheckUtils {

    private CheckUtils() {
        // utility class
    }

    public static void checkInterpolationArgs(String input, RuntimeData data, String ... dataKeys) {
        var messages = new ArrayList<String>();
        if (StringUtils.isBlank(input)) {
            messages.add(" The interpolation input is blank");
        }
        if (data == null) {
            messages.add(" The runtime data for the interpolation is null");
        } else {
            Arrays.stream(dataKeys).forEach(key -> {
                if (data.getData(key) == null) {
                    messages.add(" The '" + key + "' runtime data for the interpolation is null");
                }
            });
        }
        if (!messages.isEmpty()) {
            throw new IllegalArgumentException(Strings.join(messages, ','));
        }
    }
}
