package dev.pbroman.brat.openapi;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * Imports an OpenAPI 3.0 or 3.1 document as a starting-point suite: one request per operation, one
 * environment directory per server, and a comment wherever the author has to fill something in.
 * <p>
 * The import happens in memory and touches no disk; writing the files is the caller's. It holds no
 * state, so it may be called from several threads at once.
 */
public final class OpenApiImporter {

    private OpenApiImporter() {
        // static entry point only
    }

    // Maps the spec's security to auth and adds the token setup request once auth is imported.
    /**
     * Imports {@code content} and returns the files it becomes.
     * <p>
     * <strong>The suite.</strong> Its file is {@code <directory>.brat.yaml}, {@code <directory>} being
     * {@link Scaffold#directory()}: the spec's {@code info.title} made into a file name, or
     * {@code imported} when there is no title or nothing of it is left. Its {@code name} is the title,
     * each {@code /} a space, or {@code imported}. The first line is a comment naming
     * {@code sourceName} and the title. The spec's {@code security} is not mapped: the suite declares
     * no {@code auth}, and {@link Scaffold#setupRequests()} is {@code 0}.
     * <p>
     * <strong>Requests.</strong> One per operation. An operation's first tag puts it in the sub-suite for
     * that tag as written, named by the tag; one without a tag, or whose first tag leaves no name, stays
     * at the top of the suite. Requests keep the spec's order — paths as written, the methods of each
     * path as written — and sub-suites the order their tags first occur in. A request is named by the
     * operation's {@code summary}, else its {@code operationId}, else its method and path
     * ({@code GET orders {orderId}}); a blank one counts as absent. In any name, each {@code /} becomes a
     * space and runs of whitespace one space. Requests and sub-suites under one parent share a set of
     * names, and a name already taken there gets the lowest free suffix from {@code " 2"} on, requests
     * claiming theirs before sub-suites.
     * <p>
     * <strong>A request's definition.</strong> Its url is {@code ${env.baseUrl}} followed by the path,
     * each {@code {name}} in it replaced by {@code ${constants.name}}, then the required query parameters
     * as {@code name=${constants.name}}, joined by {@code &}. Each required header parameter is a header
     * of its name with the value {@code ${constants.name}}, except {@code Accept}, {@code Content-Type}
     * and {@code Authorization} (in any case), which are ignored. Its {@code method} is written unless it
     * is {@code GET}. Optional query and header parameters are not sent, and the url carries a comment
     * naming them. A cookie parameter is not sent either; a required one gets a comment and a note.
     * <p>
     * <strong>Constants.</strong> One per parameter name the requests send, in the order the spec first
     * uses each — one {@code ${constants.id}} serves every operation with a parameter {@code id}. The
     * first operation using it decides its value, its example written as the text of a single value, and
     * how it is written, by where that operation sends it: where the value would not survive in a url,
     * because it holds a character other than a letter, a digit or {@code -._~}, a path or query
     * constant is written percent-encoded as UTF-8 with a comment giving the example as the spec has it,
     * while a header constant is written as it is. Every constant carries a comment saying where its
     * value came from. Its value is {@code ""}, with a comment and a note, when the first operation gives
     * no example, gives a list or a mapping, or gives one holding <code>${</code>, which would be read as
     * a placeholder. Where later operations give a different example, the comment names each request and
     * its example, and a note says so.
     * <p>
     * <strong>The body.</strong> Of an operation's request-body media types, the first JSON one
     * ({@code application/json}, or any ending in {@code +json}) is used, else the first
     * {@code application/x-www-form-urlencoded}, else the first — a media type's case and its parameters
     * ({@code ; charset=UTF-8}) aside.
     * <ul>
     *   <li><em>JSON</em>: a {@code Content-Type} header of the media type as written, and the example
     *       printed as JSON — two spaces of indent, {@code "key": value}, one member or element per line.
     *       An example given as text is read as JSON first; text that is not JSON is the request's
     *       {@code raw} as it is, with a comment, whatever its length. Printed JSON of up to
     *       {@link ImportOptions#bodyFileLines()} lines is the request's {@code raw}, as a literal block;
     *       longer, it is written to {@code bodies/<name>.json} and the
     *       request's {@code file} names it, with a comment giving its length and the limit. A file name is
     *       the request's name made into a file name, or {@code body}, a taken one getting {@code -2},
     *       {@code -3}… across the whole suite. No example, or one holding <code>${</code>, gives an empty
     *       {@code raw} with a comment and a note.</li>
     *   <li><em>Form</em>: a {@code form} with one entry per property of the media type's schema, in
     *       order, each holding the example's value for it where the example is a mapping with a single
     *       value there that holds no <code>${</code>, and {@code ""} otherwise. No {@code Content-Type}
     *       is written, since a form body sets its own. An entry left {@code ""} gets a comment and a
     *       note. A schema with no properties gives an empty {@code form} with a comment and a note. A
     *       form body always stays in the suite.</li>
     *   <li><em>Any other media type</em>: a {@code Content-Type} header of it and an empty {@code raw},
     *       with a comment and a note.</li>
     * </ul>
     * <p>
     * <strong>The assertion.</strong> One: {@code ${response.statusCode}} {@code isEqualTo} the lowest
     * response code from {@code 200} to {@code 299} the operation documents. Without one — a range such as
     * {@code 2XX} is not a code — the request has no {@code responseActions}, and gets a comment and a
     * note.
     * <p>
     * <strong>Environments.</strong> One directory per server, each holding {@code env.yaml} with a
     * {@code baseUrl}: the server's url, each {@code {variable}} replaced by its default, and a trailing
     * {@code /} removed. A directory is named by the server's {@code description} made into a file name,
     * else {@code server-1}, {@code server-2}… by position; a taken name — {@code bodies} is always taken —
     * gets {@code -2}, {@code -3}…. A url that is relative (it names no scheme, like {@code /api}) or that
     * keeps a variable with no default gives {@code baseUrl: ""}, with a comment giving the url and a
     * note. A spec without servers gives one directory, {@code local}, likewise empty.
     * <p>
     * <strong>The text.</strong> Every suite and environment file loads with {@code SuiteLoader} and
     * {@code FlatYamlLoader} respectively and means what the spec said: a value taken from the spec is
     * quoted wherever YAML would otherwise read it as something else ({@code yes}, {@code 4711},
     * {@code a: b}). Every body file is JSON. Where comments are placed and how they line up is not part
     * of this contract.
     *
     * @param content the OpenAPI document, JSON or YAML; must not be {@code null}
     * @param sourceName what the header comment calls the document, such as its file name; may be
     *        {@code null}, in which case the comment names no source. A line break in it is written as a
     *        space
     * @param options the caller's choices; must not be {@code null}
     * @return the files and the notes on them
     * @throws BratException if {@code content} is not an OpenAPI 3.0 or 3.1 document the import can read
     *         — empty, not JSON or YAML, Swagger 2, another version, a {@code $ref} it needs that leaves the
     *         document or leads nowhere, or a shape it cannot read — with a message saying which and
     *         where; or if {@code content} or {@code options} is {@code null}
     */
    public static Scaffold scaffold(String content, String sourceName, ImportOptions options) {
        Require.nonNull(content, "The OpenAPI document must not be null");
        Require.nonNull(options, "The import options must not be null");
        return SuiteEmitter.emit(OpenApiLoader.load(content), sourceName, options);
    }
}
