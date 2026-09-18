package dev.pbroman.brat.core.loader;

import java.util.Map;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.exception.BratException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.node.ObjectNode;

import static dev.pbroman.brat.core.util.Constants.HTTP;
import static dev.pbroman.brat.core.util.Constants.PROTOCOL;

/**
 * Binds a {@code requestDefinition:} block to the class its {@code protocol} names.
 * <p>
 * A protocol-named field could not hold a protocol-varying type, so the discriminator sits
 * <em>inside</em> the block and this reads it: {@code protocol} names what kind of request the block
 * describes, defaults to {@code http}, and is consumed rather than bound — no definition type
 * declares a field for it, since the answer is fixed by the class that ends up being chosen.
 * <p>
 * <strong>Which classes are available is a property of the wiring, not of this type.</strong> The map
 * comes from the registered request handlers, so a document can declare exactly the protocols its
 * runner can execute, and a protocol nobody wired fails at load with a message rather than at
 * execution with a cast.
 * <p>
 * It deliberately does not use {@code @JsonTypeInfo}: that would put a serialization annotation on an
 * {@code api.*} interface every plugin implements, and presumes Jackson is the only front end this
 * document will ever have.
 */
final class RequestDefinitionDeserializer extends ValueDeserializer<RequestDefinition> {

    private final Map<String, Class<? extends RequestDefinition>> protocols;

    /**
     * Constructs a deserializer over the protocols a runner can execute.
     *
     * @param protocols the definition class per protocol name
     */
    RequestDefinitionDeserializer(Map<String, Class<? extends RequestDefinition>> protocols) {
        this.protocols = protocols;
    }

    /**
     * Reads one {@code requestDefinition:} block.
     * <p>
     * The {@code protocol} entry is removed from a copy before binding, so the chosen type sees only
     * the keys it declares and an unknown-key check stays meaningful. Binding itself is delegated, so
     * every failure inside the block is an ordinary binding failure carrying Jackson's own path —
     * which is what keeps a message's {@code line:column} intact.
     *
     * @param parser the parser positioned at the block
     * @param context the deserialization context
     * @return the bound definition
     * @throws BratException if the block is not a mapping, if {@code protocol} is not a string, or if
     *         it names a protocol this loader has no class for — the message names the protocols it
     *         does have. Note it names what <em>this loader</em> knows: a loader built by hand from a
     *         different set than the runner's will disagree with it, and the message has to let a
     *         reader see that
     */
    @Override
    public RequestDefinition deserialize(JsonParser parser, DeserializationContext context) {
        var node = context.readTree(parser);
        if (!(node instanceof ObjectNode block)) {
            throw new BratException("A requestDefinition must be a mapping");
        }

        var declared = block.get(PROTOCOL);
        if (declared != null && !declared.isString()) {
            throw new BratException("The protocol of a requestDefinition must be a single name");
        }
        var protocol = declared == null ? HTTP : declared.stringValue();

        var type = protocols.get(protocol);
        if (type == null) {
            throw new BratException("No request definition is registered for the protocol '" + protocol
                    + "'. This loader can bind: " + String.join(", ", protocols.keySet()));
        }

        // Removed from a copy rather than from the tree the caller handed us: no definition type
        // declares the key, and unknown keys are a load error, so leaving it would fail every request
        // that names its protocol.
        var payload = block.deepCopy();
        payload.remove(PROTOCOL);
        return context.readTreeAsValue(payload, type);
    }
}
