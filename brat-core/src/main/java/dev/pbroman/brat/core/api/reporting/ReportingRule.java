package dev.pbroman.brat.core.api.reporting;

/**
 * A single, priority-dispatched {@link Reporting} rule. Returning {@code null} from
 * {@link Reporting#report} (as its contract already allows) is how a rule declines a
 * {@code kind} it doesn't recognize, letting the dispatcher try the next rule.
 */
public interface ReportingRule extends Reporting {

    /**
     * Returns the priority of the rule. Rules with higher priority are executed before ones with
     * lower.
     * <p>
     * Priorities 0-100 are reserved for the core code.
     *
     * @return the priority
     */
    default int priority() {
        return 0;
    }
}
