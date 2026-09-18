package dev.pbroman.brat.core.api.data;

/**
 * What to send — the type bound narrowing what a request handler can execute to request-shaped
 * domain objects, one implementation per protocol.
 * <p>
 * A plugin adding a protocol writes a <em>sibling</em> of the built-in definitions rather than a
 * subclass of one: {@code class FtpRequestDefinition extends ConfigData implements RequestDefinition}.
 * Extending an existing definition to add a field does not work and is not meant to — interpolators
 * are looked up by exact class, so a subclass throws rather than half-working, and "HTTP plus one
 * extra knob" is the handler {@code args} bag.
 */
public interface RequestDefinition {

    /**
     * What kind of request this is, and so which handlers can execute it.
     * <p>
     * This is not an authored field. The {@code protocol:} key inside a {@code requestDefinition:}
     * block selects <em>which class</em> the block binds to, and is consumed doing so; this method is
     * that class's own answer, fixed by the type rather than read from the document.
     *
     * @return the protocol, for example {@code http}; never {@code null} or blank, and equal to the
     *         {@link dev.pbroman.brat.core.api.handler.RequestHandler#protocol()} of every handler
     *         that can execute this definition
     */
    String protocol();
}
