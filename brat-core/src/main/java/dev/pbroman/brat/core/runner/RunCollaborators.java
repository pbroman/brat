package dev.pbroman.brat.core.runner;

import java.util.List;

import dev.pbroman.brat.core.api.interpolation.ConfigDataInterpolator;
import dev.pbroman.brat.core.api.interpolation.InterpolationRule;
import dev.pbroman.brat.core.api.resolver.ConditionResolver;
import dev.pbroman.brat.core.data.Assertion;
import dev.pbroman.brat.core.data.Condition;
import dev.pbroman.brat.core.data.FlowControl;
import dev.pbroman.brat.core.interpolation.FunctionEvaluator;
import dev.pbroman.brat.core.secrets.SecretsBootstrap;

/**
 * What a runner assembles once and every {@link Run} is composed from: the collaborators with no run
 * input of their own.
 *
 * @param coreInterpolationRules core's interpolation rules, consulted before the run's secrets rule
 * @param extraInterpolationRules added and discovered interpolation rules, consulted after it
 * @param functionEvaluator evaluates {@code ${__name(…)}} calls
 * @param conditionResolver resolves every condition and assertion
 * @param secretsBootstrap builds each run's secrets chain from its environment
 * @param protocolRegistry the handlers and request-definition interpolators, by protocol
 * @param conditionInterpolator interpolates skip and poll conditions
 * @param assertionInterpolator interpolates assertions
 * @param flowControlInterpolator interpolates a request's flow control
 */
record RunCollaborators(
        List<InterpolationRule> coreInterpolationRules,
        List<InterpolationRule> extraInterpolationRules,
        FunctionEvaluator functionEvaluator,
        ConditionResolver conditionResolver,
        SecretsBootstrap secretsBootstrap,
        ProtocolRegistry protocolRegistry,
        ConfigDataInterpolator<Condition> conditionInterpolator,
        ConfigDataInterpolator<Assertion> assertionInterpolator,
        ConfigDataInterpolator<FlowControl> flowControlInterpolator) {}
