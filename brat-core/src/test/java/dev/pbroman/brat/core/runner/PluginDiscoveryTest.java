package dev.pbroman.brat.core.runner;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

import dev.pbroman.brat.core.api.handler.HttpRequestHandler;
import dev.pbroman.brat.core.api.interpolation.BratFunction;
import dev.pbroman.brat.core.api.interpolation.RequestDefinitionInterpolator;
import dev.pbroman.brat.core.data.HttpRequestDefinition;
import dev.pbroman.brat.core.data.result.HttpResponse;
import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginDiscoveryTest {

    /**
     * A loader that sees one fixture directory's {@code META-INF/services} on top of the test
     * classpath — a plugin jar, without needing a jar. The fixture directories are deliberately not
     * classpath roots themselves, so nothing here is discoverable by accident elsewhere.
     */
    private static ClassLoader loaderOver(String fixtureDirectory) {
        var root = PluginDiscoveryTest.class.getClassLoader().getResource(fixtureDirectory + "/");
        return new URLClassLoader(new URL[] {root}, PluginDiscoveryTest.class.getClassLoader());
    }

    /** Declared by the fixture's service file; must be public with a no-argument constructor. */
    /** Declared in the plugin fixture, so a builder test can prove a handler arrives by discovery. */
    public static final class DiscoverableHandler implements HttpRequestHandler {

        @Override
        public String name() {
            return "discovered-handler";
        }

        @Override
        public HttpResponse performRequest(HttpRequestDefinition requestDefinition) {
            return new HttpResponse(200, java.util.Map.of(), "discovered");
        }
    }

    /**
     * Declared in the interpolator fixture, so a builder test can prove that a definition
     * interpolator arrives by discovery too — the second collected point 8a-2 added.
     */
    public static final class DiscoverableInterpolator implements RequestDefinitionInterpolator<HttpRequestDefinition> {

        /** The URL every definition this interpolates comes back with. */
        public static final String URL = "http://replaced/by-discovery";

        @Override
        public Class<HttpRequestDefinition> definitionType() {
            return HttpRequestDefinition.class;
        }

        @Override
        public HttpRequestDefinition interpolated(
                HttpRequestDefinition target,
                dev.pbroman.brat.core.api.interpolation.Interpolation interpolation,
                dev.pbroman.brat.core.data.runtime.RuntimeData runtimeData) {
            return new HttpRequestDefinition(URL, target.getMethod(), null, null, null, null, null, java.util.Map.of());
        }
    }

    public static final class DiscoverableFunction implements BratFunction {

        @Override
        public String name() {
            return "discovered";
        }

        @Override
        public String apply(List<String> args) {
            return "discovered";
        }
    }

    @Test
    void discover_findsAnImplementationTheLoaderDeclares() {
        // when
        var found = PluginDiscovery.discover(BratFunction.class, loaderOver("plugin-fixture"));

        // then
        assertThat(found).hasSize(1).first().isInstanceOf(DiscoverableFunction.class);
    }

    @Test
    void discover_returnsEmptyWhenNothingIsDeclared() {
        // when — the ordinary case: no plugins installed
        var found = PluginDiscovery.discover(BratFunction.class, PluginDiscoveryTest.class.getClassLoader());

        // then
        assertThat(found).isEmpty();
    }

    @Test
    void discover_throwsNamingTheProviderThatCannotBeLoaded() {
        // when / then — a broken plugin fails while the runner is being built, not mid-run
        assertThatThrownBy(() -> PluginDiscovery.discover(BratFunction.class, loaderOver("broken-plugin-fixture")))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("NoSuchPluginClass");
    }

    @Test
    void discover_throwsForANullService() {
        // when / then
        assertThatThrownBy(() -> PluginDiscovery.discover(null, PluginDiscoveryTest.class.getClassLoader()))
                .isInstanceOf(BratException.class);
    }

    @Test
    void discover_throwsForANullClassLoader() {
        // when / then — a null would mean the bootstrap loader, which finds nothing and would be
        // indistinguishable from "no plugins installed"
        assertThatThrownBy(() -> PluginDiscovery.discover(BratFunction.class, null))
                .isInstanceOf(BratException.class);
    }
}
