package dev.pbroman.brat.core.runner;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import dev.pbroman.brat.core.api.listener.AttemptFinished;
import dev.pbroman.brat.core.api.listener.RunControl;
import dev.pbroman.brat.core.api.listener.RunEvent;
import dev.pbroman.brat.core.api.listener.RunListener;
import dev.pbroman.brat.core.api.secrets.SecretsProvider;
import dev.pbroman.brat.core.data.TestSuite;
import dev.pbroman.brat.core.data.result.RequestResult;
import dev.pbroman.brat.core.data.result.RunResult;
import dev.pbroman.brat.core.data.result.SuiteError;
import dev.pbroman.brat.core.data.runtime.RuntimeData;
import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.handler.ResponseActionsHandler;
import dev.pbroman.brat.core.interpolation.InterpolationRuleDispatcher;
import dev.pbroman.brat.core.interpolation.InterpolationScanner;
import dev.pbroman.brat.core.interpolation.configdata.RequestOptionsInterpolator;
import dev.pbroman.brat.core.interpolation.rules.SecretsInterpolationRule;
import dev.pbroman.brat.core.launch.Environment;
import dev.pbroman.brat.core.resolver.assertion.AssertionChainResolver;
import lombok.extern.slf4j.Slf4j;

/**
 * One execution of a suite: composes the per-run collaborators, walks the tree, and delivers the
 * run's events to its listeners.
 * <p>
 * Created per call of {@link Brat#run(TestSuite, Environment, List, RunControl)} and used once. It
 * owns the run's lifecycle guarantees, which {@code Brat.run} documents: {@code RunStarted} first,
 * {@code RunFinished} exactly once after it whatever ends the run, and a structural failure recorded
 * on the result as well as thrown.
 */
@Slf4j
final class Run {

    private final RunCollaborators collaborators;
    private final List<RunListener> listeners;
    private final RunControl runControl;
    private final List<RequestResult> results = new ArrayList<>();
    private final List<SuiteError> errors = new ArrayList<>();

    /**
     * Prepares one run.
     *
     * @param collaborators what the runner assembled once
     * @param listeners the listeners to deliver events to, in order
     * @param runControl the channel the run is stopped through
     */
    Run(RunCollaborators collaborators, List<RunListener> listeners, RunControl runControl) {
        this.collaborators = collaborators;
        this.listeners = listeners;
        this.runControl = runControl;
    }

    /**
     * Executes the run.
     *
     * @param suite the suite to run
     * @param environment the launch namespaces and secrets configuration
     * @return the run's record
     */
    RunResult execute(TestSuite suite, Environment environment) {
        var startedAt = System.currentTimeMillis();
        emit(new RunEvent.RunStarted(Instant.now()));
        RunResult result = null;
        String error = null;
        try {
            open(suite, environment);
            result = finish(startedAt, runControl.isCancelled(), null);
            return result;
        } catch (BratException e) {
            error = e.getMessage();
            throw e;
        } catch (RuntimeException | Error e) {
            // An Error ends the run as surely as an exception; a listener must not read it as a pass.
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            // In a finally so the terminal event survives a fatal error: a listener holding a file
            // handle has no other point at which to flush and close. The instance the caller gets is
            // the one the listener sees, so the two can never disagree.
            var reported = result == null ? finish(startedAt, cancelledAfterFailure(), error) : result;
            emit(new RunEvent.RunFinished(reported));
        }
    }

    /**
     * Builds the run's record from what has been collected so far.
     *
     * @param startedAt when the run began, in milliseconds
     * @param cancelled whether the run was cancelled
     * @param error what ended the run early, or {@code null}
     * @return the record
     */
    private RunResult finish(long startedAt, boolean cancelled, String error) {
        return new RunResult(results, errors, System.currentTimeMillis() - startedAt, cancelled, error);
    }

    /**
     * Whether the run was cancelled, asked once a failure has already ended it.
     * <p>
     * The control is the caller's code, and it may be what failed. Throwing again here would lose the
     * terminal event every listener is promised and replace the failure the caller is about to receive,
     * so a failure of any kind is logged and read as not cancelled — the run's own error already says
     * it did not finish.
     *
     * @return whether the run was cancelled; {@code false} if asking failed
     */
    private boolean cancelledAfterFailure() {
        try {
            return runControl.isCancelled();
        } catch (RuntimeException | Error e) {
            log.warn(
                    "The run control {} threw while the run was ending",
                    runControl.getClass().getName(),
                    e);
            return false;
        }
    }

    /**
     * Builds the run's namespaces and secrets chain, and closes the chain when the run ends.
     *
     * @param suite the suite to run
     * @param environment the launch namespaces and secrets configuration
     */
    private void open(TestSuite suite, Environment environment) {
        var runtimeData = new RuntimeData(
                suite.constants(),
                environment.env(),
                new HashMap<>(),
                environment.params(),
                environment.suiteLocation());
        // try-with-resources rather than a finally: a chain holding a lease or a file handle is built
        // per run and must not outlive it, and this is the form that suppresses a close failure when
        // the run itself threw, instead of replacing the failure the caller needs to see.
        try (var secretsProvider = collaborators.secretsBootstrap().build(environment.secretsConfig(), runtimeData)) {
            walk(suite, runtimeData, secretsProvider);
        }
    }

    /**
     * Composes the per-run collaborators around the run's secrets chain and walks the suite tree.
     *
     * @param suite the suite to run
     * @param runtimeData the run's namespaces
     * @param secretsProvider the chain {@code ${secrets.…}} resolves through
     */
    private void walk(TestSuite suite, RuntimeData runtimeData, SecretsProvider secretsProvider) {
        var rules = new ArrayList<>(collaborators.coreInterpolationRules());
        rules.add(new SecretsInterpolationRule(secretsProvider));
        rules.addAll(collaborators.extraInterpolationRules());
        var interpolation =
                new InterpolationScanner(new InterpolationRuleDispatcher(rules), collaborators.functionEvaluator());

        var conditionResolver = collaborators.conditionResolver();
        var conditionEvaluator =
                new ConditionEvaluator(interpolation, collaborators.conditionInterpolator(), conditionResolver);
        var responseHandler = new ResponseActionsHandler(
                interpolation,
                new AssertionChainResolver(interpolation, conditionResolver, collaborators.assertionInterpolator()));
        Consumer<AttemptFinished> attemptListener = this::emit;
        var processor = new RequestProcessor(
                interpolation,
                collaborators.protocolRegistry().interpolators(),
                conditionEvaluator,
                responseHandler,
                collaborators.flowControlInterpolator(),
                new RequestOptionsInterpolator(),
                new RequestExecutor(conditionEvaluator, attemptListener));

        new TestSuiteRunner(
                        processor,
                        collaborators.protocolRegistry(),
                        new SuiteEntryEvaluator(interpolation, conditionEvaluator),
                        this::emit,
                        results::add,
                        errors::add,
                        runControl)
                .walk(suite, runtimeData);
    }

    /**
     * Delivers one event to every listener, one at a time.
     *
     * @param event the event to deliver
     */
    private void emit(RunEvent event) {
        for (var listener : listeners) {
            try {
                listener.on(event);
            } catch (RuntimeException e) {
                log.warn(
                        "The listener {} threw on {}; the run continues",
                        listener.getClass().getName(),
                        event.getClass().getSimpleName(),
                        e);
            }
        }
    }
}
