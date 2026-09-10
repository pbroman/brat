package dev.pbroman.brat.core.secrets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.api.secrets.SecretsProviderFactory;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.interpolation.rules.ConstantsInterpolationRule;
import dev.pbroman.brat.core.interpolation.rules.SecretsInterpolationRule;
import org.junit.jupiter.api.Test;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.exc.StreamConstraintsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

class SecretsBootstrapTest {

    private static final String LOCATION = "location";

    private final RuntimeData runtimeData = new RuntimeData(Map.of("stage", "dev"), Map.of());

    private final List<InterpolationRule> bootstrapRules = List.of(new ConstantsInterpolationRule());

    private final Map<String, String> environment = new HashMap<>();

    private final UnaryOperator<String> envLookup = environment::get;

    // --- constructor ---

    @Test
    void constructor_letsTheLaterFactoryWinASharedType() {
        // given
        var builtIn = new StubFactory("file").serving("one.yaml", Map.of("apiKey", "from-built-in"));
        var override = new StubFactory("file").serving("one.yaml", Map.of("apiKey", "from-override"));
        var config = config(Map.of(), source("file", "one.yaml"));

        // when
        var result = bootstrap(builtIn, override).build(config, runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-override");
    }

    @Test
    void constructor_bindsTheSysenvProviderToTheProcessEnvironment() {
        // given a real variable of this JVM, restricted to a name upper-snake-casing leaves alone
        var variable = System.getenv().entrySet().stream()
                .filter(entry -> entry.getKey().matches("[A-Z][A-Z_]*"))
                .filter(entry -> !entry.getValue().isBlank())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("This JVM has no environment variable to test against"));
        var config = config(Map.of("sysenv", Map.of("prefix", "")));

        // when built through the public constructor, which is the only one binding System::getenv
        var result = new SecretsBootstrap(List.of(), bootstrapRules).build(config, runtimeData);

        // then
        assertThat(result.getSecret(variable.getKey())).contains(variable.getValue());
    }

    @Test
    void constructor_throwsIfTheFactoriesAreNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsBootstrap(null, bootstrapRules)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfAFactoryIsNull() {
        // given
        var factories = new ArrayList<SecretsProviderFactory>();
        factories.add(null);

        // when / then
        assertThatThrownBy(() -> new SecretsBootstrap(factories, bootstrapRules))
                .isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfTheBootstrapRulesAreNull() {
        // when / then
        assertThatThrownBy(() -> new SecretsBootstrap(List.of(), null)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfABootstrapRuleIsNull() {
        // given
        var rules = new ArrayList<InterpolationRule>();
        rules.add(null);

        // when / then
        assertThatThrownBy(() -> new SecretsBootstrap(List.of(), rules)).isInstanceOf(BratException.class);
    }

    @Test
    void constructor_throwsIfABootstrapRuleResolvesSecrets() {
        // given
        var rules = List.<InterpolationRule>of(new SecretsInterpolationRule(mock(SecretsProvider.class)));

        // when / then
        assertThatThrownBy(() -> new SecretsBootstrap(List.of(), rules)).isInstanceOf(BratException.class);
    }

    // --- the chain build ---

    @Test
    void build_createsOneProviderPerSource() {
        // given
        var factory = new StubFactory("file")
                .serving("one.yaml", Map.of("apiKey", "from-one"))
                .serving("two.yaml", Map.of("dbToken", "from-two"));
        var config = config(Map.of(), source("file", "one.yaml"), source("file", "two.yaml"));

        // when
        var result = bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-one");
        assertThat(result.getSecret("dbToken")).contains("from-two");
    }

    @Test
    void build_letsTheEarlierSourceWinAKeyBothDefine() {
        // given
        var factory = new StubFactory("file")
                .serving("one.yaml", Map.of("apiKey", "from-one"))
                .serving("two.yaml", Map.of("apiKey", "from-two"));
        var config = config(Map.of(), source("file", "one.yaml"), source("file", "two.yaml"));

        // when
        var result = bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-one");
    }

    @Test
    void build_appendsTheEnvironmentProviderAfterEverySource() {
        // given
        environment.put("BRAT_SECRET_API_KEY", "from-env");
        environment.put("BRAT_SECRET_DB_TOKEN", "from-env");
        var factory = new StubFactory("file").serving("one.yaml", Map.of("apiKey", "from-file"));
        var config = config(Map.of(), source("file", "one.yaml"));

        // when
        var result = bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-file");
        assertThat(result.getSecret("dbToken")).contains("from-env");
    }

    @Test
    void build_resolvesOnlyFromTheEnvironmentProviderWithoutSources() {
        // given
        environment.put("BRAT_SECRET_API_KEY", "from-env");

        // when
        var result = bootstrap().build(config(Map.of()), runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-env");
        assertThat(result.getSecret("dbToken")).isEmpty();
    }

    @Test
    void build_usesThePrefixFromTheSysenvParams() {
        // given
        environment.put("MY_API_KEY", "from-env");
        var config = config(Map.of("sysenv", Map.of("prefix", "MY_")));

        // when
        var result = bootstrap().build(config, runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-env");
    }

    @Test
    void build_usesNoPrefixForAnExplicitlyEmptyOne() {
        // given
        environment.put("API_KEY", "from-env");
        var config = config(Map.of("sysenv", Map.of("prefix", "")));

        // when
        var result = bootstrap().build(config, runtimeData);

        // then
        assertThat(result.getSecret("apiKey")).contains("from-env");
    }

    @Test
    void build_throwsForASourceTypeNoFactoryWasRegisteredFor() {
        // given
        var config = config(Map.of(), source("vault", "prod"));

        // when / then
        assertThatThrownBy(() -> bootstrap().build(config, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void build_throwsIfTheConfigIsNull() {
        // when / then
        assertThatThrownBy(() -> bootstrap().build(null, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void build_throwsIfTheRuntimeDataIsNull() {
        // when / then
        assertThatThrownBy(() -> bootstrap().build(config(Map.of()), null)).isInstanceOf(BratException.class);
    }

    @Test
    void build_propagatesAFailureFromAFactory() {
        // given
        var factory = new StubFactory("file").failing();
        var config = config(Map.of(), source("file", "one.yaml"));

        // when / then
        assertThatThrownBy(() -> bootstrap(factory).build(config, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void build_reportsTheLocationWhenAFactoryThrowsAJacksonException() {
        // given
        var factory = new StubFactory("file")
                .failingWith(new StreamConstraintsException("nesting too deep", TokenStreamLocation.NA));
        var config = config(Map.of(), source("file", "one.yaml"));

        // when / then
        assertThatThrownBy(() -> bootstrap(factory).build(config, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("The SecretsProviderFactory for type 'file' failed")
                .hasMessageContaining("at line");
    }

    @Test
    void build_closesWhatItAlreadyBuiltWhenAFactoryFails() {
        // given
        var closed = new AtomicBoolean();
        var seed = new StubFactory("seed").returning(closeTracking(closed));
        var failing = new StubFactory("file").failing();
        var config = config(Map.of(), source("seed", "seed.yaml"), source("file", "one.yaml"));

        // when
        assertThatThrownBy(() -> bootstrap(seed, failing).build(config, runtimeData))
                .isInstanceOf(BratException.class);

        // then
        assertThat(closed).isTrue();
    }

    @Test
    void build_closesWhatItAlreadyBuiltWhenAFactoryThrowsAnUncheckedException() {
        // given
        var closed = new AtomicBoolean();
        var seed = new StubFactory("seed").returning(closeTracking(closed));
        var failing = new StubFactory("file").failingWith(new IllegalStateException("no such file"));
        var config = config(Map.of(), source("seed", "seed.yaml"), source("file", "one.yaml"));

        // when
        assertThatThrownBy(() -> bootstrap(seed, failing).build(config, runtimeData))
                .isInstanceOf(BratException.class);

        // then
        assertThat(closed).isTrue();
    }

    @Test
    void build_doesNotQuoteAFactoryExceptionsMessage() {
        // given
        var failing = new StubFactory("vault").failingWith(new IllegalStateException("token s.abc123 was rejected"));
        var config = config(Map.of(), source("vault", "prod"));

        // when / then
        assertThatThrownBy(() -> bootstrap(failing).build(config, runtimeData))
                .isInstanceOf(BratException.class)
                .hasMessageNotContaining("s.abc123")
                .hasMessageContaining("vault")
                .hasMessageContaining("IllegalStateException");
    }

    @Test
    void build_closesWhatItAlreadyBuiltWhenASecretInTypeParamsIsUnresolvable() {
        // given
        var closed = new AtomicBoolean();
        var seed = new StubFactory("seed").returning(closeTracking(closed));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("token", "${secrets.vaultToken}")),
                source("seed", "seed.yaml"),
                source("vault", "prod"));

        // when
        assertThatThrownBy(() -> bootstrap(seed, vault).build(config, runtimeData))
                .isInstanceOf(BratException.class);

        // then
        assertThat(closed).isTrue();
    }

    // --- parameters handed to a factory ---

    @Test
    void build_handsAFactoryItsTypesParams() {
        // given
        var factory = new StubFactory("file");
        var config = config(Map.of("file", Map.of("charset", "UTF-8")), source("file", "one.yaml"));

        // when
        bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(factory.receivedParams)
                .singleElement()
                .satisfies(
                        params -> assertThat(params).contains(entry("charset", "UTF-8"), entry(LOCATION, "one.yaml")));
    }

    @Test
    void build_letsASourceParamOverrideItsTypesParam() {
        // given
        var factory = new StubFactory("file");
        var config = config(Map.of("file", Map.of(LOCATION, "from-connection")), source("file", "from-source"));

        // when
        bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(factory.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry(LOCATION, "from-source")));
    }

    @Test
    void build_interpolatesTheTypesParams() {
        // given
        var factory = new StubFactory("file");
        var config = config(Map.of("file", Map.of("stage", "${constants.stage}")), source("file", "one.yaml"));

        // when
        bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(factory.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry("stage", "dev")));
    }

    @Test
    void build_leavesSourceParamsUninterpolated() {
        // given
        var factory = new StubFactory("file");
        var config = config(Map.of(), source("file", "${constants.stage}"));

        // when
        bootstrap(factory).build(config, runtimeData);

        // then
        assertThat(factory.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry(LOCATION, "${constants.stage}")));
    }

    @Test
    void build_resolvesASecretEmbeddedInSurroundingText() {
        // given
        var seed = new StubFactory("seed").serving("seed.yaml", Map.of("vaultToken", "t0ken"));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("url", "https://vault/${secrets.vaultToken}/v1")),
                source("seed", "seed.yaml"),
                source("vault", "prod"));

        // when
        bootstrap(seed, vault).build(config, runtimeData);

        // then
        assertThat(vault.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry("url", "https://vault/t0ken/v1")));
    }

    @Test
    void build_resolvesEverySecretInAParamHoldingSeveralTokens() {
        // given
        var seed = new StubFactory("seed").serving("seed.yaml", Map.of("user", "admin", "password", "s3cret"));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("credentials", "${secrets.user}:${secrets.password}")),
                source("seed", "seed.yaml"),
                source("vault", "prod"));

        // when
        bootstrap(seed, vault).build(config, runtimeData);

        // then
        assertThat(vault.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry("credentials", "admin:s3cret")));
    }

    @Test
    void build_resolvesABootstrapTokenAndASecretInTheSameParam() {
        // given
        var seed = new StubFactory("seed").serving("seed.yaml", Map.of("vaultToken", "t0ken"));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("path", "${constants.stage}/${secrets.vaultToken}")),
                source("seed", "seed.yaml"),
                source("vault", "prod"));

        // when
        bootstrap(seed, vault).build(config, runtimeData);

        // then
        assertThat(vault.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry("path", "dev/t0ken")));
    }

    // --- the ordering rule ---

    @Test
    void build_resolvesASecretInTypeParamsFromAnEarlierSource() {
        // given
        var seed = new StubFactory("seed").serving("seed.yaml", Map.of("vaultToken", "t0ken"));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("token", "${secrets.vaultToken}")),
                source("seed", "seed.yaml"),
                source("vault", "prod"));

        // when
        bootstrap(seed, vault).build(config, runtimeData);

        // then
        assertThat(vault.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry("token", "t0ken")));
    }

    @Test
    void build_throwsIfASecretInTypeParamsComesFromALaterSource() {
        // given
        var seed = new StubFactory("seed").serving("seed.yaml", Map.of("vaultToken", "t0ken"));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("token", "${secrets.vaultToken}")),
                source("vault", "prod"),
                source("seed", "seed.yaml"));

        // when / then
        assertThatThrownBy(() -> bootstrap(seed, vault).build(config, runtimeData))
                .isInstanceOf(BratException.class);
    }

    @Test
    void build_throwsIfASecretInTypeParamsComesFromItsOwnSource() {
        // given
        var vault = new StubFactory("vault").serving("prod", Map.of("vaultToken", "t0ken"));
        var config = config(Map.of("vault", Map.of("token", "${secrets.vaultToken}")), source("vault", "prod"));

        // when / then
        assertThatThrownBy(() -> bootstrap(vault).build(config, runtimeData)).isInstanceOf(BratException.class);
    }

    @Test
    void build_resolvesASecretInTypeParamsFromTheEnvironmentDespiteItComingLast() {
        // given
        environment.put("BRAT_SECRET_VAULT_TOKEN", "t0ken");
        var vault = new StubFactory("vault");
        var config = config(Map.of("vault", Map.of("token", "${secrets.vaultToken}")), source("vault", "prod"));

        // when
        bootstrap(vault).build(config, runtimeData);

        // then
        assertThat(vault.receivedParams)
                .singleElement()
                .satisfies(params -> assertThat(params).contains(entry("token", "t0ken")));
    }

    @Test
    void build_interpolatesATypesParamsOnceHoweverManySourcesUseIt() {
        // given
        var lookups = new AtomicInteger();
        var seed = new StubFactory("seed").returning(counting(lookups, "vaultToken", "t0ken"));
        var vault = new StubFactory("vault");
        var config = config(
                Map.of("vault", Map.of("token", "${secrets.vaultToken}")),
                source("seed", "seed.yaml"),
                source("vault", "one"),
                source("vault", "two"));

        // when
        bootstrap(seed, vault).build(config, runtimeData);

        // then
        assertThat(vault.receivedParams).hasSize(2);
        assertThat(lookups).hasValue(1);
    }

    @Test
    void build_interpolatesATypesParamsAgainForEveryBuild() {
        // given
        environment.put("BRAT_SECRET_VAULT_TOKEN", "first");
        var vault = new StubFactory("vault");
        var config = config(Map.of("vault", Map.of("token", "${secrets.vaultToken}")), source("vault", "prod"));
        var bootstrap = bootstrap(vault);

        // when
        bootstrap.build(config, runtimeData);
        environment.put("BRAT_SECRET_VAULT_TOKEN", "second");
        bootstrap.build(config, runtimeData);

        // then
        assertThat(vault.receivedParams).hasSize(2);
        assertThat(vault.receivedParams.get(1)).contains(entry("token", "second"));
    }

    // --- helpers ---

    private SecretsBootstrap bootstrap(SecretsProviderFactory... factories) {
        return new SecretsBootstrap(List.of(factories), bootstrapRules, envLookup);
    }

    private static SecretsProviderConfig config(
            Map<String, Map<String, String>> providerParams, SecretsSource... sources) {
        return new SecretsProviderConfig(providerParams, List.of(sources));
    }

    private static SecretsSource source(String type, String location) {
        return new SecretsSource(type, Map.of(LOCATION, location));
    }

    private static SecretsProvider closeTracking(AtomicBoolean closed) {
        return new SecretsProvider() {
            @Override
            public Optional<String> getSecret(String key) {
                return Optional.empty();
            }

            @Override
            public void close() {
                closed.set(true);
            }
        };
    }

    private static SecretsProvider counting(AtomicInteger lookups, String key, String value) {
        return requested -> {
            lookups.incrementAndGet();
            return key.equals(requested) ? Optional.of(value) : Optional.empty();
        };
    }

    private static final class StubFactory implements SecretsProviderFactory {

        private final String type;

        private final Map<String, Map<String, String>> secretsByLocation = new HashMap<>();

        private final List<Map<String, String>> receivedParams = new ArrayList<>();

        private SecretsProvider fixedProvider;

        private RuntimeException failure;

        private StubFactory(String type) {
            this.type = type;
        }

        private StubFactory serving(String location, Map<String, String> secrets) {
            secretsByLocation.put(location, secrets);
            return this;
        }

        private StubFactory returning(SecretsProvider provider) {
            this.fixedProvider = provider;
            return this;
        }

        private StubFactory failing() {
            return failingWith(new BratException("This factory always fails"));
        }

        private StubFactory failingWith(RuntimeException failure) {
            this.failure = failure;
            return this;
        }

        @Override
        public String type() {
            return type;
        }

        @Override
        public SecretsProvider create(Map<String, String> params) {
            receivedParams.add(params);
            if (failure != null) {
                throw failure;
            }
            return fixedProvider != null
                    ? fixedProvider
                    : new MapSecretsProvider(secretsByLocation.getOrDefault(params.get(LOCATION), Map.of()));
        }
    }
}
