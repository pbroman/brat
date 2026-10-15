package dev.pbroman.brat.core.auth;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import dev.pbroman.brat.core.api.auth.AuthHandler;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * The auth handlers a runner can select, keyed by {@link AuthHandler#type()}.
 */
@Slf4j
public final class AuthHandlerRegistry {

    private final Map<String, AuthHandler> registry;

    /**
     * Constructs a registry over the auth handlers a runner has available.
     * <p>
     * The order of {@code handlers} matters only for repeated types: a handler whose type repeats an
     * earlier one <strong>replaces</strong> it, and the replacement is logged at WARN naming both
     * classes. That is how a registration overrides a built-in type, so the caller puts core's first.
     *
     * @param handlers the handlers, in registration order; must not be {@code null}, and may be empty.
     *        Not retained — a later change to the collection does not affect the registry
     * @throws BratException if {@code handlers} is {@code null}, holds
     *         a {@code null} element, or holds a handler whose {@link AuthHandler#type()} is
     *         {@code null} or blank — naming that handler's class
     */
    public AuthHandlerRegistry(Collection<? extends AuthHandler> handlers) {
        Require.noNullElements(handlers, "The handlers may not be null or contain any null elements.");
        var handlersWithNullType = handlers.stream()
                .filter(handler -> StringUtils.isBlank(handler.type()))
                .map(handler -> handler.getClass().getName())
                .toList();
        if (!handlersWithNullType.isEmpty()) {
            throw new BratException(String.format(
                    "Following auth handlers have type null or blank: %s. Each handler must have a defined type.",
                    String.join(", ", handlersWithNullType)));
        }
        var byType = new TreeMap<String, AuthHandler>();
        for (AuthHandler handler : handlers) {
            var registered = byType.put(handler.type(), handler);
            if (registered != null) {
                log.warn(
                        "AuthHandler '{}' overrides '{}', since both have type '{}'",
                        handler.getClass().getName(),
                        registered.getClass().getName(),
                        handler.type());
            }
        }
        registry = Collections.unmodifiableSortedMap(byType);
    }

    /**
     * Looks up the handler for an auth type.
     *
     * @param type the type, matched exactly, case included; must not be {@code null}
     * @return the handler registered for {@code type}; never {@code null}
     * @throws BratException if {@code type} is {@code null}, or if no
     *         handler is registered for it — the message names the type and every registered type, in
     *         sorted order
     */
    public AuthHandler get(String type) {
        Require.nonNull(type, "The auth handler type must not be null");
        var handler = registry.get(type);
        if (handler == null) {
            throw new BratException(String.format(
                    "There is no handler registered for type '%s'. Registered handlers: %s", type, types()));
        }
        return handler;
    }

    /**
     * Every type a handler is registered for.
     *
     * @return the types in sorted order, unmodifiable; never {@code null}, empty only for a registry
     *         built over no handlers
     */
    public Set<String> types() {
        return registry.keySet();
    }
}
