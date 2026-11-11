package io.github.brayandm.seeder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeederRunnerTest {
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void runsTheTargetWithItsDependencies(boolean parallel) {
    var users = new Users();
    var posts = new Posts();
    var runner = new SeederRunner(List.of(posts, users));
    var options = new SeedOptions(parallel, 2, 42);

    runner.run(Posts.class, options);
    runner.runAll(options);

    assertEquals(List.of(6L, 6L), posts.ids);
    assertEquals(users.samples.getFirst(), users.samples.getLast());
    var failure =
        assertThrows(IllegalStateException.class, () -> runner.runOnly(Posts.class, options));
    assertTrue(failure.getCause().getMessage().contains("no result in this run"));
  }

  @Test
  void rejectsMissingDependenciesAndCycles() {
    var missing = new SeederRunner(List.of(new Posts()));
    var cyclic = new SeederRunner(List.of(new First(), new Second()));

    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> missing.runAll(SeedOptions.DEFAULT))
            .getMessage()
            .contains("Missing seeder"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> cyclic.runAll(SeedOptions.DEFAULT))
            .getMessage()
            .contains("First -> Second -> First"));
  }

  @Test
  void rejectsReadsOfUndeclaredDependencies() {
    var reader = new UndeclaredReader();
    var runner = new SeederRunner(List.of(new Users(), reader));

    var failure =
        assertThrows(
            IllegalStateException.class, () -> runner.runAll(new SeedOptions(false, 1, 0)));
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
  }

  @Test
  void runsIndependentTasksConcurrentlyAndPropagatesFailures() {
    var started = new CountDownLatch(2);
    var dependent = new MustNotRun();
    var runner =
        new SeederRunner(List.of(dependent, new Failing(started), new Independent(started)));

    var failure =
        assertThrows(IllegalStateException.class, () -> runner.runAll(SeedOptions.DEFAULT));

    assertEquals("intentional failure", failure.getCause().getMessage());
    assertFalse(dependent.ran);
  }

  @Test
  void rejectsDuplicateRegistrationsAndUnknownTargets() {
    assertThrows(
        IllegalArgumentException.class, () -> new SeederRunner(List.of(new Users(), new Users())));

    var runner = new SeederRunner(List.of());
    assertThrows(
        IllegalArgumentException.class, () -> runner.run(Users.class, SeedOptions.DEFAULT));
    assertThrows(
        IllegalArgumentException.class, () -> runner.runOnly(Users.class, SeedOptions.DEFAULT));
  }

  @Test
  void runsOnlyTheTargetWhenItsDependenciesAreRegistered() {
    var users = new Users();
    var target = new StandaloneTarget();
    new SeederRunner(List.of(users, target)).runOnly(StandaloneTarget.class, SeedOptions.DEFAULT);

    assertTrue(users.samples.isEmpty());
    assertTrue(target.ran);
  }

  @Seeder
  private static final class Users implements SeedTask<List<Long>> {
    private final List<Integer> samples = new ArrayList<>();

    @Override
    public List<Long> run(SeedContext context) {
      samples.add(context.random().nextInt());
      return List.of(context.scaled(3));
    }
  }

  @Seeder(dependsOn = Users.class)
  private static final class Posts implements SeedTask<Void> {
    private final List<Long> ids = new ArrayList<>();

    @Override
    public Void run(SeedContext context) {
      List<Long> users = context.get(Users.class);
      ids.addAll(users);
      return null;
    }
  }

  private static final class UndeclaredReader implements SeedTask<List<Long>> {
    @Override
    public List<Long> run(SeedContext context) {
      return context.get(Users.class);
    }
  }

  @Seeder(dependsOn = Second.class)
  private static final class First implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      throw new AssertionError("Cycle should prevent execution");
    }
  }

  @Seeder(dependsOn = First.class)
  private static final class Second implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      throw new AssertionError("Cycle should prevent execution");
    }
  }

  private record Failing(CountDownLatch started) implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) throws InterruptedException {
      started.countDown();
      assertTrue(started.await(5, TimeUnit.SECONDS));
      throw new IllegalArgumentException("intentional failure");
    }
  }

  private record Independent(CountDownLatch started) implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      started.countDown();
      return null;
    }
  }

  @Seeder(dependsOn = Failing.class)
  private static final class MustNotRun implements SeedTask<Void> {
    private boolean ran;

    @Override
    public Void run(SeedContext context) {
      ran = true;
      return null;
    }
  }

  @Seeder(dependsOn = Users.class)
  private static final class StandaloneTarget implements SeedTask<Void> {
    private boolean ran;

    @Override
    public Void run(SeedContext context) {
      ran = true;
      return null;
    }
  }
}
