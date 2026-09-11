package dev.pbroman.brat.integration.app;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The server BRAT's suites run against: an in-memory CRUD API over users, plus the handful of
 * endpoints that exist only so a suite can reach a BRAT behaviour no unit test can show.
 *
 * <p><strong>The endpoints:</strong>
 *
 * <table border="1">
 *   <caption>what the server answers</caption>
 *   <tr><th>endpoint</th><th>answers</th><th>what a suite reaches through it</th></tr>
 *   <tr><td>{@code POST /create}</td><td>201, the created user</td>
 *       <td>a body to assert on; 409 on a duplicate username, 400 with no username</td></tr>
 *   <tr><td>{@code GET /user/{id}}</td><td>200, one user; 404</td>
 *       <td>a captured id read back; a 404 body</td></tr>
 *   <tr><td>{@code GET /search/{username}}</td><td>200, a list</td>
 *       <td>an array body, empty or not</td></tr>
 *   <tr><td>{@code GET /all}</td><td>200, every user</td><td>a list whose size a suite counts</td></tr>
 *   <tr><td>{@code POST /update}</td><td>200, the updated user</td>
 *       <td>400 without an id, 404 for an unknown one, 409 on a username clash</td></tr>
 *   <tr><td>{@code DELETE /delete/{id}}</td><td>204, no body; 404</td>
 *       <td>an empty body, and deleting the same user twice</td></tr>
 *   <tr><td>{@code DELETE /clear}</td><td>204</td><td>the clean slate every test starts from</td></tr>
 *   <tr><td>{@code POST /job?readyAfter=n}</td><td>201, a pending job</td>
 *       <td>something to poll</td></tr>
 *   <tr><td>{@code GET /job/{id}}</td><td>200, {@code pending} until polled {@code n} times</td>
 *       <td>{@code repeatUntil}: its attempts, its waits, and giving up</td></tr>
 *   <tr><td>{@code GET /slow?delayMs=n}</td><td>200, after a delay</td>
 *       <td>a request outliving its own {@code timeout}</td></tr>
 *   <tr><td>{@code GET /echo}</td><td>200, the request's headers</td>
 *       <td>what actually reached the wire - a header, later an auth header</td></tr>
 *   <tr><td>{@code GET /text}</td><td>200, {@code text/plain}</td>
 *       <td>{@code ${response.body}} where {@code ${response.json}} cannot go</td></tr>
 *   <tr><td>{@code GET /status/{code}}</td><td>the code asked for, with a body</td>
 *       <td>a 5xx being a response rather than an error</td></tr>
 * </table>
 *
 * <p>{@code User} carries the fields that make a response body worth asserting on: a nested object, an
 * array, a number, a boolean, a timestamp and a nullable.
 *
 * <p>State is in-memory and shared, so every test clears it first and the module runs sequentially.
 */
@SpringBootApplication
@RestController
public class CrudApplication {

    /** "Database" of users, keyed by id; usernames are unique. */
    private final Map<String, User> users = new ConcurrentHashMap<>();

    /** Polling jobs, keyed by id. */
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();

    // --- users ---

    @PostMapping(path = "/create", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public User createUser(@RequestBody User submitted) {
        if (submitted.username() == null) {
            throw new BadRequest("A username must be provided");
        }
        if (findByUsername(submitted.username()) != null) {
            throw new ResourceAlreadyPresent("User with username " + submitted.username() + " already exists");
        }
        var user = submitted.created(UUID.randomUUID().toString(), Instant.now());
        users.put(user.id(), user);
        return user;
    }

    @GetMapping(path = "/user/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public User readUser(@PathVariable String id) {
        var user = users.get(id);
        if (user == null) {
            throw new ResourceNotFoundException("User with id " + id + " not found");
        }
        return user;
    }

    @GetMapping(path = "/search/{username}", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<User> searchUser(@PathVariable String username) {
        var found = new ArrayList<User>();
        for (var user : users.values()) {
            if (username.equals(user.username())) {
                found.add(user);
            }
        }
        return found;
    }

    @GetMapping(path = "/all", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<User> allUsers() {
        return new ArrayList<>(users.values());
    }

    @PostMapping(path = "/update", produces = MediaType.APPLICATION_JSON_VALUE)
    public User updateUser(@RequestBody User submitted) {
        if (submitted.id() == null) {
            throw new BadRequest("User id must be provided");
        }
        var existing = users.get(submitted.id());
        if (existing == null) {
            throw new ResourceNotFoundException("User with id " + submitted.id() + " not found");
        }
        if (submitted.username() != null) {
            var clash = findByUsername(submitted.username());
            if (clash != null && !clash.id().equals(existing.id())) {
                throw new ResourceAlreadyPresent("User with username " + submitted.username() + " already exists");
            }
        }
        var updated = existing.updatedWith(submitted);
        users.put(updated.id(), updated);
        return updated;
    }

    @DeleteMapping(path = "/delete/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable String id) {
        if (users.remove(id) == null) {
            throw new ResourceNotFoundException("User with id " + id + " not found");
        }
    }

    /** Resets everything a test might have left behind — users and jobs alike. */
    @DeleteMapping(path = "/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear() {
        users.clear();
        jobs.clear();
    }

    // --- endpoints that exist for BRAT rather than for CRUD ---

    /**
     * Starts a job that reports {@code pending} until it has been read {@code readyAfter} times.
     *
     * @param readyAfter how many polls precede the first {@code ready}; {@code 0} is ready at once
     * @return the job, never yet polled
     */
    @PostMapping(path = "/job", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Job startJob(@RequestParam(defaultValue = "2") int readyAfter) {
        var state = new JobState(UUID.randomUUID().toString(), readyAfter, new AtomicInteger());
        jobs.put(state.id(), state);
        return state.view();
    }

    /** Reads a job, counting the read — the poll itself is what moves the job towards ready. */
    @GetMapping(path = "/job/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Job readJob(@PathVariable String id) {
        var state = jobs.get(id);
        if (state == null) {
            throw new ResourceNotFoundException("Job with id " + id + " not found");
        }
        state.polls().incrementAndGet();
        return state.view();
    }

    /** Answers after a delay, so a suite can write a {@code timeout} the server outlives. */
    @GetMapping(path = "/slow", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> slow(@RequestParam(defaultValue = "1000") long delayMs) throws InterruptedException {
        Thread.sleep(delayMs);
        return Map.of("delayedMs", delayMs);
    }

    /** Echoes the request's own headers, so a suite can assert what actually reached the wire. */
    @GetMapping(path = "/echo", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> echo(@RequestHeader Map<String, String> headers) {
        return Map.of("headers", new HashMap<>(headers));
    }

    /** A body that is not JSON, for {@code ${response.body}} where {@code ${response.json}} cannot go. */
    @GetMapping(path = "/text", produces = MediaType.TEXT_PLAIN_VALUE)
    public String text() {
        return "a plain text body";
    }

    /** Answers with the status code asked for, since every status a server sends is a response. */
    @GetMapping(path = "/status/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> status(@PathVariable int code) {
        return ResponseEntity.status(code).body(Map.of("requested", code));
    }

    // --- internals ---

    private User findByUsername(String username) {
        for (var user : users.values()) {
            if (username.equals(user.username())) {
                return user;
            }
        }
        return null;
    }

    /**
     * A user, with the fields a suite asserts on.
     *
     * @param id server-assigned on create, and what an update is addressed by
     * @param username unique across the database
     * @param email optional, so a suite has something to assert {@code isNull} about
     * @param age a JSON number, so a suite can compare numerically across the wire
     * @param active a JSON boolean, defaulting to {@code true} on create
     * @param createdAt server-assigned, ISO-8601, for the date funcs
     * @param roles a JSON array, for indexed JSONPath
     * @param address a nested object, for dotted JSONPath
     */
    public record User(
            String id,
            String username,
            String email,
            Integer age,
            Boolean active,
            Instant createdAt,
            List<String> roles,
            Address address) {

        /** Fills in what the server owns: the id, the creation time, and the defaults. */
        User created(String id, Instant createdAt) {
            return new User(
                    id,
                    username,
                    email,
                    age,
                    active == null ? Boolean.TRUE : active,
                    createdAt,
                    roles == null ? List.of() : List.copyOf(roles),
                    address);
        }

        /** Overlays every field the submitted user actually carries; id and createdAt never change. */
        User updatedWith(User submitted) {
            return new User(
                    id,
                    submitted.username() == null ? username : submitted.username(),
                    submitted.email() == null ? email : submitted.email(),
                    submitted.age() == null ? age : submitted.age(),
                    submitted.active() == null ? active : submitted.active(),
                    createdAt,
                    submitted.roles() == null ? roles : List.copyOf(submitted.roles()),
                    submitted.address() == null ? address : submitted.address());
        }
    }

    /** A nested object, so a suite can reach {@code ${response.json.$.address.city}}. */
    public record Address(String street, String city, String zip) {}

    /** What a poll of a job returns. */
    public record Job(String id, String status, int polls) {}

    /** A job's own state; {@code readyAfter} is the server's business and is never published. */
    private record JobState(String id, int readyAfter, AtomicInteger polls) {

        Job view() {
            var read = polls.get();
            return new Job(id, read > readyAfter ? "ready" : "pending", read);
        }
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class ResourceNotFoundException extends RuntimeException {
        ResourceNotFoundException(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    static class ResourceAlreadyPresent extends RuntimeException {
        ResourceAlreadyPresent(String message) {
            super(message);
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    static class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }
}
