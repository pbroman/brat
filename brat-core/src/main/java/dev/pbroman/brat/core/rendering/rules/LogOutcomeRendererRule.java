package dev.pbroman.brat.core.rendering.rules;

import java.util.stream.Collectors;

import dev.pbroman.brat.core.api.rendering.OutcomeRendererRule;
import dev.pbroman.brat.core.api.rendering.RenderTarget;

/**
 * The core {@code "log"} {@link OutcomeRendererRule} — a single {@code logfmt}-style line
 * ({@code key="value"} pairs, space-separated, quote-escaped), meant for log aggregators
 * rather than a human reader.
 * <p>
 * A label is emitted as a leading {@code _label="…"} pair. The underscore marks it as
 * renderer-supplied metadata and keeps it out of the key space the outcomes occupy, which holds
 * authored field names — without it, an outcome named after the metadata key would produce a line
 * carrying that key twice, and a {@code logfmt} parser would keep one of the two arbitrarily.
 * <p>
 * A target with no outcomes renders as its label pair alone, or as the empty string when it has no
 * label either — never with a trailing separator.
 */
public final class LogOutcomeRendererRule implements OutcomeRendererRule {

    private static final String KIND = "log";

    private static final String LABEL_KEY = "_label";

    @Override
    public String render(String kind, RenderTarget target) {
        if (!KIND.equals(kind)) {
            return null;
        }
        var pairs = target.outcomes().entrySet().stream()
                .map(entry -> entry.getKey() + "=\"" + escape(entry.getValue().reportingString()) + "\"")
                .collect(Collectors.joining(" "));
        if (target.label() == null) {
            return pairs;
        }
        var labelPair = LABEL_KEY + "=\"" + escape(target.label()) + "\"";
        return pairs.isEmpty() ? labelPair : labelPair + " " + pairs;
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
