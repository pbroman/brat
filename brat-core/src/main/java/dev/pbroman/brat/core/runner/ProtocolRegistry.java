package dev.pbroman.brat.core.runner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.data.RequestDefinition;
import dev.pbroman.brat.core.api.handler.RequestHandler;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.configdata.RequestDefinitionInterpolators;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;

/**
 * What a runner knows about protocols: which handlers are registered, which of them a request goes
 * to, and which class a protocol's authored block binds to.
 * <p>
 * Everything here is fixed when a runner is built, and every inconsistency it can see is reported
 * then rather than on the request that trips over it — a handler naming a protocol nothing can
 * interpolate, two handlers disagreeing about a protocol's definition class, a default naming a
 * handler nobody registered.
 */
@Slf4j
final class ProtocolRegistry {

    private final Map<String, Map<String, RequestHandler<?, ?>>> byProtocol = new TreeMap<>();
    private final Map<String, Class<? extends RequestDefinition>> definitionTypes = new LinkedHashMap<>();
    private final Map<String, String> defaultNames;
    private final RequestDefinitionInterpolators interpolators;

    /**
     * Registers the handlers and checks that what they declare hangs together.
     * <p>
     * Registration order is the caller's statement of precedence: <strong>the last registration of a
     * {@code (protocol, name)} pair wins</strong>, logged at WARN naming both implementations, since
     * no order survives to lookup time and two handlers under one name can only be an overlay. That
     * is what lets a plugin replace a built-in handler while every suite goes on naming it.
     *
     * @param handlers the handlers to register, in precedence order — later wins; never {@code null},
     *        possibly empty, though a runner with none can perform no request
     * @param defaultNames the handler to use for a protocol when a suite names none, keyed by
     *        protocol; never {@code null}, possibly empty
     * @param interpolators the interpolators available for request definitions; never {@code null}
     * @throws BratException if any argument is {@code null}, or if a handler declares a {@code null}
     *         or blank {@code protocol()}, {@code name()} or {@code definitionType()}
     * @throws BratException if two handlers for one protocol declare different
     *         {@code definitionType()}s. That is an inconsistency rather than an overlay — both stay
     *         selectable, and a document can bind to only one class per protocol — so resolving it by
     *         last-wins would produce a {@code ClassCastException} one layer later
     * @throws BratException if a registered protocol's definition type has no interpolator. A plugin
     *         shipping a handler and forgetting its interpolator fails here, named, rather than on
     *         its first request
     * @throws BratException if {@code defaultNames} names a handler that is not registered for that
     *         protocol, or names a protocol with no handlers at all
     */
    ProtocolRegistry(
            List<RequestHandler<?, ?>> handlers,
            Map<String, String> defaultNames,
            RequestDefinitionInterpolators interpolators) {
        Require.nonNull(handlers, "The handlers must not be null");
        Require.nonNull(defaultNames, "The default handler names must not be null");
        Require.nonNull(interpolators, "The request definition interpolators must not be null");
        this.defaultNames = Map.copyOf(defaultNames);
        this.interpolators = interpolators;
        for (var handler : handlers) {
            register(handler);
        }
        checkInterpolators();
        checkDefaults();
    }

    /**
     * Adds one handler under its own two-part key, and records what its protocol binds to.
     *
     * @param handler the handler to register
     */
    private void register(RequestHandler<?, ?> handler) {
        Require.nonNull(handler, "A request handler must not be null");
        var protocol = handler.protocol();
        Require.nonBlank(protocol, declaresNo("protocol", handler));
        var name = handler.name();
        Require.nonBlank(name, declaresNo("name", handler));
        var definitionType = handler.definitionType();
        Require.nonNull(
                definitionType,
                "The definitionType of the request handler "
                        + handler.getClass().getName() + " must not be null");

        var known = definitionTypes.putIfAbsent(protocol, definitionType);
        if (known != null && !known.equals(definitionType)) {
            throw new BratException("The handlers for the protocol '" + protocol + "' disagree about what a "
                    + "requestDefinition binds to: " + known.getName() + " and " + definitionType.getName()
                    + ". One protocol has one definition type");
        }

        var forProtocol = byProtocol.computeIfAbsent(protocol, p -> new TreeMap<>());
        var replaced = forProtocol.put(name, handler);
        if (replaced != null) {
            log.warn(
                    "The request handler '{}' for protocol '{}' was registered twice: {} is replaced by {}",
                    name,
                    protocol,
                    replaced.getClass().getName(),
                    handler.getClass().getName());
        }
    }

    /**
     * The message for a key a handler failed to declare.
     *
     * @param what which key it is
     * @param handler the handler that failed to declare it
     * @return the message
     */
    private static String declaresNo(String what, RequestHandler<?, ?> handler) {
        return "The request handler " + handler.getClass().getName() + " declares no " + what
                + ", so nothing can select it";
    }

    /**
     * Checks that every registered protocol's definition type can be interpolated.
     */
    private void checkInterpolators() {
        for (var binding : definitionTypes.entrySet()) {
            if (!interpolators.has(binding.getValue())) {
                throw new BratException("The protocol '" + binding.getKey() + "' binds to "
                        + binding.getValue().getName() + ", which has no interpolator registered. Its fields would "
                        + "never be interpolated");
            }
        }
    }

    /**
     * Checks that every configured default names a handler that exists.
     */
    private void checkDefaults() {
        for (var configured : defaultNames.entrySet()) {
            var forProtocol = byProtocol.get(configured.getKey());
            if (forProtocol == null || !forProtocol.containsKey(configured.getValue())) {
                throw new BratException("The default request handler for protocol '" + configured.getKey() + "' is '"
                        + configured.getValue() + "', which is not registered. " + candidates(forProtocol));
            }
        }
    }

    /**
     * The handler that executes {@code definition}, given what the suite tree asked for.
     * <p>
     * Four rungs, tried in order: the name {@code names} declares for the definition's protocol; the
     * default configured for that protocol; the sole handler registered for it; otherwise this fails.
     * <strong>Never list order</strong> — plugin discovery order is unspecified, so position is an
     * order nobody authored, and a handler appearing on the classpath must not silently change which
     * client every suite uses.
     *
     * @param definition the request to be executed, whose {@code protocol()} is dispatched on; never
     *        {@code null}
     * @param names the handler a suite or request names per protocol, already merged down the tree by
     *        the caller; never {@code null}, possibly empty
     * @return the handler, typed for a caller that only holds a {@link RequestDefinition}
     * @throws BratException if either argument is {@code null}
     * @throws BratException if no handler is registered for the definition's protocol, if the name
     *         asked for is not among them, or if several are registered with no default to choose
     *         between them — each naming the protocol and the registered candidates. This is
     *         structural: a suite naming a handler is not portable to a wiring that lacks it, and
     *         failing loudly is the point
     * @throws BratException if the resolved handler does not execute this definition's class, which
     *         means a wiring inconsistency the registry could not see at construction
     */
    @SuppressWarnings("unchecked")
    RequestHandler<RequestDefinition, Object> resolve(RequestDefinition definition, Map<String, String> names) {
        Require.nonNull(definition, "The request definition must not be null");
        Require.nonNull(names, "The request handler names must not be null");
        var protocol = definition.protocol();
        // A protocol's entry is created with a handler in it and nothing ever removes one, so an
        // entry that exists is never empty - absent is the only way to have no handler.
        var forProtocol = byProtocol.get(protocol);
        if (forProtocol == null) {
            throw new BratException(
                    "No request handler is registered for the protocol '" + protocol + "'. " + registeredProtocols());
        }

        var handler = select(protocol, forProtocol, names.get(protocol));
        if (!handler.definitionType().isInstance(definition)) {
            throw new BratException("The request handler '" + handler.name() + "' for protocol '" + protocol
                    + "' executes " + handler.definitionType().getName() + ", which the request definition "
                    + definition.getClass().getName() + " is not");
        }
        return (RequestHandler<RequestDefinition, Object>) handler;
    }

    /**
     * Picks one of a protocol's handlers, by what was asked for and then by what was configured.
     *
     * @param protocol the protocol being resolved, for the messages
     * @param forProtocol the handlers registered for it, keyed by name
     * @param asked the name the suite tree asked for, or {@code null} for none
     * @return the handler to use
     */
    private RequestHandler<?, ?> select(String protocol, Map<String, RequestHandler<?, ?>> forProtocol, String asked) {
        if (asked != null) {
            var named = forProtocol.get(asked);
            if (named == null) {
                throw new BratException("No request handler named '" + asked + "' for protocol '" + protocol + "'. "
                        + candidates(forProtocol));
            }
            return named;
        }
        var configured = defaultNames.get(protocol);
        if (configured != null) {
            return forProtocol.get(configured);
        }
        if (forProtocol.size() == 1) {
            return forProtocol.values().iterator().next();
        }
        throw new BratException("Several request handlers are registered for protocol '" + protocol
                + "' and none is the default, so a request must name one. " + candidates(forProtocol));
    }

    /**
     * Which class an authored {@code requestDefinition:} block binds to, per protocol.
     *
     * @return an unmodifiable map of protocol to definition class, which is what lets a document
     *         declare exactly the protocols this runner can execute; never {@code null}
     */
    Map<String, Class<? extends RequestDefinition>> protocolBindings() {
        return Map.copyOf(definitionTypes);
    }

    /**
     * The interpolators for request definitions, checked against the registered protocols.
     *
     * @return the interpolators this registry was built with; never {@code null}
     */
    RequestDefinitionInterpolators interpolators() {
        return interpolators;
    }

    /**
     * Names the handlers registered for one protocol, for a message.
     *
     * @param forProtocol the handlers, or {@code null} when the protocol has none — never empty,
     *        since an entry is only created with a handler in it
     * @return a sentence naming them
     */
    private static String candidates(Map<String, RequestHandler<?, ?>> forProtocol) {
        if (forProtocol == null) {
            return "No handler is registered for that protocol";
        }
        return "Registered: " + String.join(", ", forProtocol.keySet());
    }

    /**
     * Names the protocols with a handler, for a message.
     *
     * @return a sentence naming them
     */
    private String registeredProtocols() {
        if (byProtocol.isEmpty()) {
            return "No request handler is registered at all";
        }
        return "Registered protocols: " + String.join(", ", byProtocol.keySet());
    }
}
