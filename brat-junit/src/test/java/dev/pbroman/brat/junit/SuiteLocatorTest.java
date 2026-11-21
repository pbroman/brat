package dev.pbroman.brat.junit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import dev.pbroman.brat.core.exception.BratException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The search a relative suite path goes through, over a small repository laid out under a temporary
 * directory:
 *
 * <pre>
 * temp/
 *   repo/
 *     .git/
 *     api-tests/orders-api/orders-api.brat.yaml
 *     service/
 *       target/
 * </pre>
 */
class SuiteLocatorTest {

    private static final String SUITE = "api-tests/orders-api";

    @TempDir
    private Path temp;

    private Path repo;
    private Path service;

    @BeforeEach
    void layOut() throws IOException {
        repo = Files.createDirectories(temp.resolve("repo"));
        Files.createDirectory(repo.resolve(".git"));
        Files.createDirectories(repo.resolve(SUITE));
        Files.writeString(repo.resolve(SUITE).resolve("orders-api.brat.yaml"), "name: orders\n");
        service = Files.createDirectories(repo.resolve("service/target"));
        service = service.getParent();
    }

    @Test
    void locate_findsThePathInTheStartDirectory() {
        // when
        var found = SuiteLocator.locate(SUITE, repo);

        // then
        assertThat(found).isEqualTo(repo.resolve(SUITE));
    }

    @Test
    void locate_findsThePathInADirectoryAbove() {
        // when
        var found = SuiteLocator.locate(SUITE, service.resolve("target"));

        // then
        assertThat(found).isEqualTo(repo.resolve(SUITE));
    }

    @Test
    void locate_findsAFileAsWellAsADirectory() {
        // when
        var found = SuiteLocator.locate(SUITE + "/orders-api.brat.yaml", service);

        // then
        assertThat(found).isEqualTo(repo.resolve(SUITE).resolve("orders-api.brat.yaml"));
    }

    @Test
    void locate_prefersTheNearestDirectory() throws IOException {
        // given
        Files.createDirectories(service.resolve(SUITE));

        // when
        var found = SuiteLocator.locate(SUITE, service);

        // then
        assertThat(found).isEqualTo(service.resolve(SUITE));
    }

    @Test
    void locate_returnsAnAbsolutePathAsGivenWithoutSearching() {
        // given
        var absolute = temp.resolve("nowhere/orders-api").toAbsolutePath();

        // when
        var found = SuiteLocator.locate(absolute.toString(), service);

        // then
        assertThat(found).isEqualTo(absolute);
    }

    @Test
    void locate_stopsAtTheRepositoryRootNamingIt() throws IOException {
        // given - above the repository, where the search must not reach
        Files.createDirectories(temp.resolve("elsewhere"));

        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate("elsewhere", service))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("'elsewhere'")
                .hasMessageContaining(service.toString())
                .hasMessageContaining("up to " + repo + " (the repository root)");
    }

    @Test
    void locate_takesAGitFileAsTheRepositoryRootToo() throws IOException {
        // given - a worktree or submodule: .git is a file
        var worktree = Files.createDirectories(temp.resolve("worktree/service"));
        Files.writeString(worktree.getParent().resolve(".git"), "gitdir: ../repo/.git/worktrees/w\n");
        Files.createDirectories(temp.resolve("api-tests/orders-api"));

        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate(SUITE, worktree))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("up to " + worktree.getParent() + " (the repository root)");
    }

    @Test
    void locate_searchesUpToTheFilesystemRootOutsideARepository() throws IOException {
        // given - no .git anywhere on the way up from here
        var outside = Files.createDirectories(temp.resolve("plain/module"));
        Files.createDirectories(temp.resolve("plain/suites/x"));
        assumeTrue(noGitAbove(temp), "the temporary directory lies inside a repository");

        // when
        var found = SuiteLocator.locate("suites/x", outside);

        // then
        assertThat(found).isEqualTo(temp.resolve("plain/suites/x"));
    }

    @Test
    void locate_namesTheFilesystemRootWhenNothingIsFoundOutsideARepository() throws IOException {
        // given
        var outside = Files.createDirectories(temp.resolve("plain/module"));
        assumeTrue(noGitAbove(temp), "the temporary directory lies inside a repository");

        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate("no-such-suite-anywhere", outside))
                .isInstanceOf(BratException.class)
                .hasMessageContaining("(the filesystem root)");
    }

    @Test
    void locate_climbsTheStartAsGivenWithoutResolvingSymbolicLinks() throws IOException {
        // given - service is a link to a directory outside the repository
        var real = Files.createDirectories(temp.resolve("real/service"));
        var link = repo.resolve("linked-service");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "symbolic links are not available here");
        }

        // when
        var found = SuiteLocator.locate(SUITE, link);

        // then - found from the link's own parent, the repository, not from real/
        assertThat(found).isEqualTo(repo.resolve(SUITE));
    }

    @Test
    void locate_treatsADirectoryItMayNotReadAsNotHoldingThePath() throws IOException {
        // given - the suite sits in a directory the search may not look into, nearer than the repository's
        var locked = Files.createDirectories(service.resolve("locked"));
        Files.createDirectories(locked.resolve(SUITE));
        var start = Files.createDirectories(locked.resolve("inner"));
        try {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("---------"));
        } catch (UnsupportedOperationException e) {
            assumeTrue(false, "POSIX permissions are not available here");
        }
        try {
            assumeTrue(!Files.exists(locked.resolve(SUITE)), "this user reads every directory, as root does");

            // when
            var found = SuiteLocator.locate(SUITE, start);

            // then - the locked copy is passed over, the repository's found
            assertThat(found).isEqualTo(repo.resolve(SUITE));
        } finally {
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  "})
    void locate_refusesABlankPath(String path) {
        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate(path, service)).isInstanceOf(BratException.class);
    }

    @Test
    void locate_refusesANullPath() {
        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate(null, service)).isInstanceOf(BratException.class);
    }

    @Test
    void locate_refusesSomethingThatIsNotAPath() {
        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate("a\u0000b", service)).isInstanceOf(BratException.class);
    }

    @Test
    void locate_refusesANullStart() {
        // when / then
        assertThatThrownBy(() -> SuiteLocator.locate(SUITE, null)).isInstanceOf(BratException.class);
    }

    private static boolean noGitAbove(Path directory) {
        for (var dir = directory.toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(".git"))) {
                return false;
            }
        }
        return true;
    }
}
