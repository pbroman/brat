package dev.pbroman.brat.core.api.auth;

import java.util.Map;

import dev.pbroman.brat.core.data.Auth;
import dev.pbroman.brat.core.data.HttpRequestDefinition;

/**
 * One authentication mechanism for HTTP requests: it turns an {@code auth:} block of its
 * {@link #type()} into the headers that authenticate a request.
 * <p>
 * <strong>Selected by type.</strong> A suite names the mechanism in {@code auth: {type: …}}, and the
 * handler whose {@link #type()} matches is the one called. Handlers are collected from core, then the
 * runner's builder, then {@code META-INF/services/dev.pbroman.brat.core.api.auth.AuthHandler}; a later
 * one with the same type replaces an earlier one, logged at WARN — which is how a built-in type is
 * overridden.
 * <p>
 * <strong>Stateless.</strong> One instance serves every run of a runner, possibly runs that overlap,
 * so it must hold nothing that belongs to a run — no cached token, no counter. Everything it needs
 * arrives as arguments.
 * <p>
 * <strong>HTTP only.</strong> Auth applies to HTTP requests; a request of another protocol declaring a
 * type other than {@code none} fails before any handler is called.
 * <p>
 * <strong>Closing.</strong> Implementing {@link AutoCloseable} is optional. A handler the runner
 * discovered is closed when the runner is; one handed to the builder is closed by whoever created it.
 */
public interface AuthHandler {

    /**
     * The type this handler is selected by, such as {@code "basic"}.
     * <p>
     * Matched exactly, case included, against the {@code type} of an {@code auth:} block. It must be
     * the same on every call.
     *
     * @return the type; never {@code null} or blank
     */
    String type();

    /**
     * Returns the headers that authenticate {@code request} with {@code auth}.
     * <p>
     * <strong>Called on every attempt</strong> of the request, after it has been interpolated and
     * before it is sent — so a polling request calls it once per attempt, and a value that must be
     * fresh, such as a signing timestamp, is computed here each time. Each call receives the request
     * as interpolated, never one carrying headers from an earlier call.
     * <p>
     * <strong>Returns headers, and only headers.</strong> Core adds them to a copy of the request and
     * sends that; the request passed in is never modified, and must not be modified here. The URL,
     * method and body are there to be read — a signature covers them — not to be changed.
     * <p>
     * <strong>Never returns a header the request already carries.</strong> Core compares names
     * ignoring case, and a request whose author wrote a header this returns ends in error, since it
     * would state two credentials for one request.
     * <p>
     * <strong>Handles credentials.</strong> No value from {@code auth.getArgs()}, nor anything derived
     * from one, belongs in a log line or an exception message: name the argument, never its value.
     * Every returned header is reported masked.
     *
     * @param auth the request's auth block, interpolated: its {@code type} is this handler's
     *        {@link #type()} and every value in its {@code args} is resolved. Never {@code null}
     * @param request the request about to be sent, interpolated; never {@code null}
     * @return the headers to add, by name; never {@code null}, possibly empty. No name or value is
     *         {@code null} or blank, and no two names differ only in case
     * @throws dev.pbroman.brat.core.exception.BratException if {@code auth.getArgs()} holds a key this
     *         handler does not know, lacks one it requires, or holds a value it cannot use — a blank
     *         credential among them — or if the headers cannot be computed. The request then ends in
     *         error with this message, and the run goes on
     */
    Map<String, String> apply(Auth auth, HttpRequestDefinition request);
}
