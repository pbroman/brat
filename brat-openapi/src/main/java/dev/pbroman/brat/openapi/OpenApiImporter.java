package dev.pbroman.brat.openapi;

import dev.pbroman.brat.core.exception.BratException;
import dev.pbroman.brat.core.util.Require;

/**
 * Imports an OpenAPI 3.0, 3.1 or 3.2 document as a starting-point suite: one request per operation, one
 * environment directory per server, and a comment wherever the author has to fill something in.
 * <p>
 * The import happens in memory and touches no disk; writing the files is the caller's. It holds no
 * state, so it may be called from several threads at once.
 */
public final class OpenApiImporter {

    private OpenApiImporter() {
        // static entry point only
    }

    /**
     * Imports {@code content} and returns the files it becomes.
     * <p>
     * <strong>The suite.</strong> Its file is {@code <directory>.brat.yaml}, {@code <directory>} being
     * {@link Scaffold#directory()}: the spec's {@code info.title} made into a file name, or
     * {@code imported} when there is no title or nothing of it is left. Its {@code name} is the title,
     * each {@code /} a space, or {@code imported}. The first line is a comment naming
     * {@code sourceName} and the title.
     * <p>
     * <strong>Requests.</strong> One per operation. An operation's first tag puts it in the sub-suite for
     * that tag as written, named by the tag; one without a tag, or whose first tag leaves no name, stays
     * at the top of the suite. <strong>When no operation has a tag</strong>, the first segment of each
     * path that is neither a {@code {parameter}} nor a version — {@code v1}, {@code v2beta},
     * {@code 2010-04-01} — takes a tag's place, so {@code /v1/accounts/{id}} goes to {@code accounts}; a
     * segment only one operation would go to keeps that operation at the top instead. Requests keep the
     * spec's order — paths as written, the methods of each path as written — and sub-suites the order
     * their tags or segments first occur in. A request is named by the
     * operation's {@code summary}, else its {@code operationId}, else its method and path
     * ({@code GET orders {orderId}}); a blank one counts as absent. In any name, each {@code /} becomes a
     * space and runs of whitespace one space. Requests and sub-suites under one parent share a set of
     * names, and a name already taken there gets the lowest free suffix from {@code " 2"} on, requests
     * claiming theirs before sub-suites.
     * <p>
     * <strong>A request's definition.</strong> Its url is {@code ${env.baseUrl}} followed by the path,
     * each {@code {name}} in it replaced by {@code ${constants.<constant>}}, then the required query
     * parameters as {@code name=${constants.<constant>}}, joined by {@code &} — {@code <constant>} being the
     * name of the constant that parameter is sent from, below. Each required header parameter is a header
     * of its name with the value {@code ${constants.<constant>}}, except {@code Accept}, {@code Content-Type}
     * and {@code Authorization} (in any case), which are ignored. Its {@code method} is written unless it
     * is {@code GET}. Optional query and header parameters are not sent, and the url carries a comment
     * naming them. A cookie parameter is not sent either; a required one gets a comment and a note.
     * <p>
     * <strong>Constants.</strong> One per parameter the requests send, in the order the spec first uses
     * each, where a parameter is a name together with what the spec makes it: its {@code in}, its schema's
     * type and enum, and for an array its items' type and enum. Parameters alike in all of these share a
     * constant — one {@code ${constants.orderId}} serves every operation sending that {@code orderId}.
     * Of parameters sharing a name and not the rest, the first met keeps the bare name and each other is
     * named {@code <name>_<segment>}: {@code <segment>} is the last segment of its first operation's path
     * that is not a {@code {parameter}}, each run of characters other than a letter or a digit made one
     * {@code _} and any at either end dropped ({@code /me/top/{type}} → {@code type_top}); where that
     * leaves nothing, or the name is already taken, the lowest free of {@code <name>_2}, {@code <name>_3}….
     * <p>
     * A constant's value is the first <em>usable</em> example among the operations it serves, in spec
     * order — one that is a single value holding no <code>${</code> — written as the text of a single
     * value, and how it is written is decided by where the operation giving it sends it: where the value
     * would not survive in a url, because it holds a character other than a letter, a digit or
     * {@code -._~}, a path or query constant is written percent-encoded as UTF-8 with a comment giving the
     * example as the spec has it, while a header constant is written as it is. Without a usable example,
     * its value is the first of the parameter's enum values, with a comment naming them — at most five,
     * then {@code …}. Without either, the constant is <strong>written commented out</strong>,
     * {@code # <name>:}, with a comment and a note saying to fill it in or pass
     * {@code -p constants.<name>=…}; a request sending it then errors before anything is sent,
     * {@code The constant '<name>' is not set}, rather than sending a blank. Where no constant has a
     * value, the {@code constants:} key is commented out as well. Every constant carries a
     * comment saying where its value came from; one named with a suffix adds
     * {@code the "<parameter name>" of "<its first request>", not <the bare one's first request>'}.
     * Where the operations a constant serves give different examples, the comment names each later
     * request and its example, and a note says so.
     * <p>
     * <strong>The body.</strong> A {@code GET} or {@code HEAD} sends none, whatever the spec declares;
     * where it declares one, a note says so. Otherwise, of an operation's request-body media types, the
     * first JSON one
     * ({@code application/json}, or any ending in {@code +json}) is used, else the first
     * {@code application/x-www-form-urlencoded}, else the first — a media type's case and its parameters
     * ({@code ; charset=UTF-8}) aside.
     * <ul>
     *   <li><em>JSON</em>: a {@code Content-Type} header of the media type as written, and the example
     *       printed as JSON — two spaces of indent, {@code "key": value}, one member or element per line.
     *       An example given as text is read as JSON where it is an object or a list
     *       ({@code '{"item":"book"}'}); any other text is a JSON string, printed as one ({@code "."}).
     *       An example given as {@code serializedValue} is already in its final form: it is the request's
     *       {@code raw} as it is, with a comment, whatever its length. <strong>Without an example</strong>,
     *       the body is built from the schema: a value's own example, else its first enum value; an
     *       object of its properties, each that has a value and each required one, a required one without
     *       a value written as a blank of its type ({@code ""}, {@code 0}, {@code false}, {@code []},
     *       {@code {}} — an object built of its own required properties); an array of one element, its
     *       items' value. A property neither required nor having a value is left out. A built body
     *       carries a comment on {@code body:} — {@code built from the schema's examples}, then
     *       {@code ; fill in: } and the required properties written blank, nested ones as
     *       {@code customer.name} — and, where any is blank, a note. Printed JSON of up to
     *       {@link ImportOptions#bodyFileLines()} lines is the request's {@code raw}, as a literal block;
     *       longer, it is written to {@code bodies/<name>.json} and the
     *       request's {@code file} names it, with a comment giving its length and the limit. A file name is
     *       the request's name made into a file name, or {@code body}, a taken one getting {@code -2},
     *       {@code -3}… across the whole suite. No example and nothing to build, or a body holding
     *       <code>${</code>, gives an empty {@code raw} with a comment and a note.</li>
     *   <li><em>Form</em>: a {@code form} with one entry per <em>required</em> property of the media
     *       type's schema, in order, each holding the example's value for it where the example is a
     *       mapping with a single value there that holds no <code>${</code>, and {@code ""} otherwise. The
     *       optional properties are named in a comment on {@code form:} — {@code optional: a, b} — at most
     *       ten, then {@code … and N more}. No {@code Content-Type} is written, since a form body sets its
     *       own. An entry left {@code ""} gets a comment and a note; a required property that is an object
     *       or a list gets {@code ""} with a comment saying to send it as {@code <name>[<key>]} entries.
     *       Without required properties the {@code form} is empty, with the comment naming the optional
     *       ones. A form whose schema has no properties but whose example is a mapping has one entry per
     *       key of the example, in order, written as above, with a comment saying so. <strong>A form whose
     *       schema has no properties at all, and whose media type has no example, is no body</strong>: the
     *       request sends none, and a note says the spec declared an empty form. A form body always stays
     *       in the suite.</li>
     *   <li><em>Any other media type</em>: a {@code Content-Type} header of it and an empty {@code raw},
     *       with the comment {@code write the <media type> body here} and a note.</li>
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
     * A directory's name is taken from the {@code description}'s first four words, a leading
     * {@code a}, {@code an} or {@code the} left out and trailing filler words — {@code a}, {@code an},
     * {@code the}, {@code and}, {@code or}, {@code of}, {@code for}, {@code to}, {@code in}, {@code on},
     * {@code at}, {@code with}, {@code by} — dropped, then cut to at most 30 characters at a word's end
     * where one fits: {@code The staging server, reset nightly} → {@code staging-server-reset-nightly},
     * {@code The production server for the Train Travel API} → {@code production-server}.
     * <p>
     * <strong>An operation's own server.</strong> Where a path or an operation names servers of its own,
     * the first is the one its request goes to. One whose url equals a document server's goes to
     * {@code ${env.baseUrl}} as any other. Each other distinct url, variables replaced by their
     * defaults, is an {@code env} key of its own: {@code <label>BaseUrl}, {@code <label>} the url's first
     * host label in lower case, letters and digits only ({@code https://uploads.github.com} →
     * {@code uploadsBaseUrl}), {@code server} where that leaves nothing — no host, or a label still
     * holding a variable — and {@code 2}, {@code 3}… after
     * it on a clash. The key is written to <em>every</em> environment's {@code env.yaml}, after
     * {@code baseUrl}, with the url and a comment naming its requests — at most three, then
     * {@code and N more} — and saying it is the same in every environment, since the spec gives one url
     * for all. Its requests' urls start with {@code ${env.<key>}}. A relative url ({@code /v2}) is
     * {@code ${env.baseUrl}} followed by it, with no key; one keeping a variable without a default gives
     * the key {@code ""} with a comment and a note.
     * <p>
     * <strong>Auth.</strong> The document's {@code security} becomes the suite's {@code auth}; a
     * request whose operation's security maps to a different one carries its own, and one that maps to
     * none where the suite declares one carries {@code type: none}. Of a security's alternatives the
     * first is used, and of its schemes the first; one the spec requires as well gets a comment and a
     * note — on the url, for an api key in the query, which has no {@code auth} to carry it. Each
     * {@code auth} carries a comment naming the scheme it came from; a comment saying what to do about
     * it — obtain a token — sits on its {@code type:} line.
     * <p>
     * <strong>Where an {@code auth} is written.</strong> Where at least two requests of a sub-suite carry
     * their own {@code auth} and all of them carry the same one, it is written once, on the sub-suite,
     * with the comment {@code the spec's security for every request here: <scheme>}, and not on the
     * requests. Where the document declares no {@code security}, there is no token request, and every
     * request of the suite carries the same {@code auth} of its own — at least two of them — it is
     * written once on the suite instead, with {@code the spec's security for every request: <scheme>}.
     * <ul>
     *   <li>{@code http} {@code bearer}: {@code bearer} with {@code ${secrets.token}};</li>
     *   <li>{@code http} {@code basic}: {@code basic} with {@code ${secrets.username}} and
     *       {@code ${secrets.password}};</li>
     *   <li>{@code apiKey} in a header: {@code apikey} with {@code ${secrets.apiKey}} and that header;</li>
     *   <li>{@code apiKey} in the query: no {@code auth}, and {@code <name>=${secrets.apiKey}} added to the
     *       url's query, percent-encoding the name as a query parameter's;</li>
     *   <li>{@code oauth2} with a client-credentials flow: {@code bearer} with {@code ${vars.accessToken}},
     *       set by a setup request named {@code get a token}, the first request of the suite. It is
     *       skipped when the run starts with a token already there — {@code skipCondition}
     *       {@code ${vars.accessToken}} {@code isNotBlank}, with a comment saying so — so a launch passing
     *       {@code vars.accessToken} sends that token instead of fetching one. It carries
     *       {@code type: none}, posts a {@code form} of {@code grant_type: client_credentials},
     *       {@code client_id: ${env.clientId}}, {@code client_secret: ${secrets.clientSecret}} and
     *       {@code scope} — every scope the spec's uses of the scheme name, in the order first named,
     *       left out if there is none — to {@code ${env.tokenUrl}}, asserts status {@code 200} and sets
     *       {@code accessToken} from {@code ${response.json.$.access_token}}. Every environment gets
     *       {@code tokenUrl}, the spec's, and {@code clientId: ""} with a comment and a note;</li>
     *   <li>{@code oauth2} without a client-credentials flow, and {@code openIdConnect}: {@code bearer}
     *       with {@code ${secrets.token}}, with a comment and a note saying to obtain the token;</li>
     *   <li>anything else — another {@code http} scheme, {@code apiKey} in a cookie or without a
     *       {@code name}, {@code mutualTLS}, or a scheme the document does not define: {@code type: none},
     *       with a comment and a note saying the scheme is not imported.</li>
     * </ul>
     * {@code security: []}, or a first alternative naming no scheme, maps to none. Two schemes that would
     * use the same {@code secrets}, {@code env} or {@code vars} key do not share it: the one the spec uses
     * first keeps it, each later one gets its own name in front ({@code ${secrets.admin.token}}), and its
     * token request is named {@code get a token for <scheme>}. A scheme no security maps to adds nothing.
     * Where a {@code ${secrets.…}} is first written, a comment names the environment variable it is read
     * from by default ({@code BRAT_SECRET_CLIENT_SECRET}) and a note carries its key in
     * {@link Scaffold.Note#secret()}. {@link Scaffold#setupRequests()} counts the token requests.
     * <p>
     * <strong>What is not imported.</strong> Each entry of what the document holds and the import does
     * not read — its {@code webhooks}, a path's {@code additionalOperations} — is a note:
     * {@code not imported: webhooks: orderShipped}. A spec with webhooks and no operations also gets a
     * comment under the suite's first line saying so.
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
