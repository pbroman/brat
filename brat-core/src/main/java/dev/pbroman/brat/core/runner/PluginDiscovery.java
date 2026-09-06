package dev.pbroman.brat.core.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import dev.pbroman.brat.core.exception.BratException;

import static dev.pbroman.brat.core.util.Require.nonNull;

/**
 * Finds the implementations of one extension point that a classloader can see, via
 * {@link java.util.ServiceLoader}.
 * <p>
 * The registration path for a plugin jar: it declares its implementations in
 * {@code META-INF/services/<interface>} and BRAT finds them with no configuration and no BRAT-specific
 * manifest. Discovery happens once, when the runner is built.
 */
// Never ServiceLoader.load(Class): that uses the thread-context loader, which is ambient, mutable
// and may be null, so the same build() would discover different plugins depending on the calling
// thread - the shape of a defect that reproduces in CI and not locally.
final class PluginDiscovery {

    private PluginDiscovery() {
        // no instances
    }

    /**
     * Every implementation of {@code service} that {@code classLoader} can see, in the order
     * {@link java.util.ServiceLoader} yields them.
     * <p>
     * <strong>The classloader is always explicit</strong> — this never consults the thread-context
     * loader, so the same inputs discover the same plugins whichever thread calls it.
     * <p>
     * <strong>A broken plugin fails here, not later.</strong> Each provider is instantiated during
     * this call, so a missing class, an unimplemented interface or a throwing constructor surfaces
     * while the runner is being built rather than in the middle of a run.
     *
     * @param service the extension-point interface to look for; never {@code null}
     * @param classLoader the loader to search; never {@code null} — a {@code null} would mean the
     *        bootstrap loader, which finds nothing and would look like "no plugins installed"
     * @return the implementations found, in {@code ServiceLoader} order; empty when there are none,
     *         which is the ordinary case
     * @throws BratException if either argument is {@code null}, or if a declared provider cannot be
     *         loaded or instantiated — the message names the service and the offending provider
     */
    static <T> List<T> discover(Class<T> service, ClassLoader classLoader) {
        nonNull(service, "The service to discover implementations of must not be null");
        nonNull(classLoader, "The classloader to discover through must not be null");
        // The whole iteration is guarded, not each provider: ServiceLoader resolves classes lazily, so
        // a provider naming a class that does not exist fails while the iterator advances - before any
        // Provider handle exists to name it. Its own message names the offending class either way.
        try {
            var found = new ArrayList<T>();
            for (T implementation : ServiceLoader.load(service, classLoader)) {
                found.add(implementation);
            }
            return List.copyOf(found);
        } catch (ServiceConfigurationError | RuntimeException e) {
            throw new BratException(
                    "A plugin declared as a " + service.getSimpleName() + " could not be loaded: " + e.getMessage(), e);
        }
    }
}
