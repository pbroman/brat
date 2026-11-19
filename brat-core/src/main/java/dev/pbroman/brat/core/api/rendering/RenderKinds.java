package dev.pbroman.brat.core.api.rendering;

/**
 * The render kinds core renders, as passed to {@link OutcomeRenderer#render}: a rule answering one of
 * these — to override how core renders it — and a caller asking for one both name it from here.
 */
public final class RenderKinds {

    /** One line per outcome under the target's label, as the console reporter prints a run. */
    public static final String CONSOLE = "console";

    /** The outcomes grouped into sections by key prefix ({@code header.}, {@code auth.}), for a terminal. */
    public static final String VERBOSE_CLI = "verbose-cli";

    /** A single {@code logfmt}-style line, for a log aggregator rather than a human reader. */
    public static final String LOG = "log";

    /** A record-style {@code Label[key=value, ...]} dump. */
    public static final String UNITTEST = "unittest";

    private RenderKinds() {}
}
