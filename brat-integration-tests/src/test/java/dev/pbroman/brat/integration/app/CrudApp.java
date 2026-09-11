package dev.pbroman.brat.integration.app;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Boots {@link CrudApplication} once for the whole module run and says where it is listening.
 *
 * <p><strong>Once per JVM, not once per class.</strong> Booting in a {@code @BeforeAll} would start
 * the server again for every aspect class; this starts it on the first call from anywhere and leaves it
 * up for the rest of the run.
 *
 * <p><strong>A synchronized accessor rather than the lazy-holder idiom</strong>, which is the shorter
 * way to write this and the worse one here: a failure inside a class initializer becomes an
 * {@code ExceptionInInitializerError}, after which every later test reports only
 * {@code NoClassDefFoundError: Could not initialize class …} with no cause attached. Booting inside a
 * method means a failure is rethrown, with its cause, to every caller that asks — which is what a
 * reader needs when the server will not start.
 *
 * <p><strong>Nothing closes the context here</strong>: Spring Boot registers a shutdown hook for it
 * itself ({@code spring.main.register-shutdown-hook}, on by default), and the server's useful life is
 * the test JVM's. A second hook would only add a second path to the same close.
 *
 * <p><strong>This package is the only one allowed to import Spring</strong> (see the scoped
 * suppression in {@code config/checkstyle/checkstyle.xml}). Spring hosts the server under test;
 * nothing here wires BRAT, and a test class that tried to have Spring wire it would fail the build
 * rather than quietly becoming a starter test.
 *
 * <p>The port is whatever the OS hands out, so the module never collides with something already
 * listening on a fixed one.
 */
public final class CrudApp {

    private static String baseUrl;

    private CrudApp() {}

    /**
     * Returns the base URL the running application answers on, booting it if it is not up yet.
     *
     * @return a URL with no trailing slash, e.g. {@code http://localhost:41923}
     * @throws RuntimeException whatever Spring threw, if the application cannot start — rethrown on
     *         every call rather than once
     */
    public static synchronized String baseUrl() {
        if (baseUrl == null) {
            baseUrl = boot();
        }
        return baseUrl;
    }

    /**
     * Starts the application on a free port.
     *
     * @return the base URL it is listening on
     */
    private static String boot() {
        var application = new SpringApplication(CrudApplication.class);
        application.setDefaultProperties(Map.of(
                // 0 = any free port; asked back out of the context below.
                "server.port", "0",
                "spring.main.banner-mode", "off"));
        return "http://localhost:" + portOf(application.run());
    }

    /**
     * The port the application's own web server bound.
     *
     * @param context the started context
     * @return the bound port
     */
    private static int portOf(ConfigurableApplicationContext context) {
        if (context instanceof WebServerApplicationContext web && web.getWebServer() != null) {
            return web.getWebServer().getPort();
        }
        throw new IllegalStateException("The CRUD application started without a web server");
    }
}
