package dev.pbroman.brat.core.data.runtime;

/**
 * The mark left in {@code vars} by a {@code setVars} entry that failed — captured from a response or
 * computed when a suite is entered — so that a later read of that variable can say why it is missing
 * and where it went wrong.
 * <p>
 * It exists because {@code vars} is the one namespace that soft-fails: an unset {@code ${vars.x}}
 * logs a warning and resolves to the empty string, so without this a failed entry turns into a URL
 * that quietly becomes {@code /orders/} and a 404 three requests later whose assertion names nothing
 * about the cause. The tombstone is what connects the two.
 * <p>
 * Deliberately not the same type as {@code CaptureFailure}, which they are easy to confuse. A
 * {@code CaptureFailure} is reported on the request that failed and lives as long as that request's
 * result; a tombstone lives in the run's {@code vars} until something sets the key successfully, and
 * its whole purpose is to be found by a *different* node. Different lifetime, different reader.
 *
 * @param message why the entry failed
 * @param path the path of the request or suite whose entry failed — the point of the type, since
 *        whoever reads it is standing somewhere else
 */
public record VarTombstone(String message, String path) {}
