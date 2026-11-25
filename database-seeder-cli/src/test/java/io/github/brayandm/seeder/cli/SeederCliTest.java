package io.github.brayandm.seeder.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeederCliTest {
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

  @Test
  void runsAllByDefault() {
    var users = new Users();
    var posts = new Posts();
    var other = new Other();

    assertEquals(0, run(List.of(posts, users, other)));
    assertEquals(3, posts.userCount);
    assertTrue(other.ran);
    var progress = output.toString(StandardCharsets.UTF_8);
    assertTrue(progress.contains("Started Users"));
    assertTrue(progress.indexOf("Completed Users") < progress.indexOf("Started Posts"));
    assertTrue(progress.lines().anyMatch("Completed Posts"::equals));
    assertFalse(progress.contains("started="));
    assertFalse(progress.contains(" ms"));
    assertFalse(progress.contains("["));
    assertFalse(progress.contains("Run completed"));
  }

  @Test
  void selectsTargetWithDependenciesAndAppliesScaleAndLinearMode() {
    var users = new Users();
    var posts = new Posts();
    var other = new Other();

    assertEquals(0, run(List.of(posts, users, other), "--seeder=Posts", "--scale=2", "--linear"));

    assertEquals(6, posts.userCount);
    assertEquals(Thread.currentThread(), users.thread);
    assertFalse(other.ran);
  }

  @Test
  void onlySkipsRegisteredDependencies() {
    var users = new Users();
    var target = new Standalone();

    assertEquals(0, run(List.of(users, target), "--seeder=Standalone", "--only"));
    assertTrue(target.ran);
    assertNull(users.thread);
  }

  @Test
  void asksForQualifiedNameWhenSimpleNameIsAmbiguous() {
    var billing = new Billing.Users();
    var support = new Support.Users();
    var seeders = List.of(billing, support);

    assertEquals(2, run(seeders, "--seeder=Users"));
    var message = errors.toString(StandardCharsets.UTF_8);
    assertTrue(message.contains(Billing.Users.class.getName()));
    assertTrue(message.contains(Support.Users.class.getName()));
    assertFalse(billing.ran);
    assertFalse(support.ran);

    assertEquals(0, run(seeders, "--seeder=" + Billing.Users.class.getCanonicalName()));
    assertTrue(billing.ran);
    assertFalse(support.ran);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "--only",
        "--scale=0",
        "--scale=NaN",
        "--scale=abc",
        "--seeder",
        "--seeder=missing",
        "--linear=true",
        "--max-parallel",
        "--max-parallel=0",
        "--max-parallel=-1",
        "--max-parallel=1.5",
        "--max-parallel=2147483648",
        "--unknown"
      })
  void rejectsInvalidArgumentsBeforeRunning(String argument) {
    var other = new Other();

    assertEquals(2, run(List.of(other), argument));
    assertFalse(other.ran);
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("--help"));
  }

  @Test
  void listsFullNamesInOrderWithoutRunningSeeders() {
    var billing = new Billing.Users();
    var support = new Support.Users();

    assertEquals(0, run(List.of(support, billing), "--list", "--verbose"));
    assertEquals(
        List.of(Billing.Users.class.getCanonicalName(), Support.Users.class.getCanonicalName()),
        output.toString(StandardCharsets.UTF_8).lines().toList());
    assertFalse(billing.ran);
    assertFalse(support.ran);
  }

  @Test
  void rejectsSelectionFlagsWhenListing() {
    assertEquals(2, run(List.of(new Other()), "--list", "--seeder=Other"));
    assertEquals(2, run(List.of(new Other()), "--list", "--only"));
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("--list cannot be combined"));
  }

  @Test
  void helpPrintsUsageWithoutRunningSeeders() {
    var other = new Other();

    assertEquals(0, run(List.of(other), "--help"));
    assertFalse(other.ran);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Usage:"));
  }

  @Test
  void reportsExecutionErrors() {
    assertEquals(1, run(List.of(new Posts())));
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Missing seeder"));
    assertFalse(output.toString(StandardCharsets.UTF_8).contains("Started "));
  }

  @Test
  void dryRunReportsMaxParallelAndRejectsCombiningItWithLinear() {
    assertEquals(0, run(List.of(new Other()), "--dry-run", "--max-parallel=4"));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("max-parallel=4"));
    assertEquals(2, run(List.of(), "--linear", "--max-parallel=4"));
  }

  @Test
  void dryRunShowsThatIndependentSeedersRunOneAtATimeWithASingleSlot() {
    assertEquals(
        0, run(List.of(new Posts(), new Users(), new Other()), "--dry-run", "--max-parallel=1"));
    assertEquals(
        List.of(
            "Dry run (one at a time, scale=1.0, max-parallel=1)",
            "1. "
                + Users.class.getName()
                + ", "
                + Other.class.getName()
                + " (independent, one at a time)",
            "2. " + Posts.class.getName()),
        output.toString(StandardCharsets.UTF_8).lines().toList());
  }

  @Test
  void dryRunGroupsIndependentSeedersWithoutRunningThem() {
    var users = new Users();
    var posts = new Posts();
    var other = new Other();

    assertEquals(0, run(List.of(posts, users, other), "--dry-run"));
    assertEquals(
        List.of(
            "Dry run (parallel, scale=1.0)",
            "1. " + Users.class.getName() + ", " + Other.class.getName() + " (can run in parallel)",
            "2. " + Posts.class.getName()),
        output.toString(StandardCharsets.UTF_8).lines().toList());
    assertNull(users.thread);
    assertEquals(0, posts.userCount);
    assertFalse(other.ran);
  }

  @Test
  void dryRunPlansOnlyTheSelectedSeederWhenRequested() {
    assertEquals(0, run(List.of(new Posts()), "--dry-run", "--only", "--seeder=Posts", "--linear"));
    assertEquals(
        List.of("Dry run (linear, scale=1.0)", "1. " + Posts.class.getName()),
        output.toString(StandardCharsets.UTF_8).lines().toList());
  }

  @Test
  void dryRunValidatesDependenciesOfTheSelectedSeeder() {
    assertEquals(1, run(List.of(new Posts()), "--dry-run", "--seeder=Posts"));
    assertEquals(
        List.of("Missing seeder: " + Users.class.getName() + " in Posts"),
        errors.toString(StandardCharsets.UTF_8).lines().toList());
    assertEquals(0, output.size());
  }

  @Test
  void dryRunReportsTheCycleWithoutAStackTrace() {
    assertEquals(1, run(List.of(new Cyclic()), "--dry-run"));
    assertEquals(
        List.of("Circular dependency: Cyclic -> Cyclic"),
        errors.toString(StandardCharsets.UTF_8).lines().toList());
    assertEquals(0, output.size());
  }

  @Test
  void rejectsCombiningDryRunAndList() {
    assertEquals(2, run(List.of(), "--dry-run", "--list"));
  }

  @Test
  void flushesProgressBeforeTheSeederFinishes() {
    var buffered = new PrintStream(new BufferedOutputStream(output), false, StandardCharsets.UTF_8);
    SeedTask<Void> task =
        context -> {
          var progress = output.toString(StandardCharsets.UTF_8);
          assertTrue(progress.startsWith("Started "));
          assertFalse(progress.contains("Completed "));
          return null;
        };

    assertEquals(0, SeederCli.run(new String[0], List.of(task), buffered, System.err));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Completed "));
  }

  @Test
  void verboseReportsTheEffectiveConcurrencyLimit() {
    assertEquals(0, run(List.of(new Other()), "--verbose", "--max-parallel=1"));
    var settings =
        output.toString(StandardCharsets.UTF_8).lines().skip(1).findFirst().orElseThrow();
    assertEquals(
        "  mode=one at a time scale=1.0 seed=0 target=all only=false max-parallel=1", settings);
  }

  @Test
  void reportsFailedSeedersAndVerboseDetails() {
    assertEquals(1, run(List.of(new Broken(), new Blocked()), "--verbose", "--linear"));

    var progress = output.toString(StandardCharsets.UTF_8);
    assertTrue(progress.startsWith("["));
    assertTrue(progress.contains("mode=sequential scale=1.0"));
    assertTrue(progress.contains("Started " + Broken.class.getName()));
    assertTrue(progress.contains("Failed " + Broken.class.getName() + " in "));
    assertFalse(progress.contains(Blocked.class.getName()));
    assertTrue(progress.contains("started=1, completed=0, failed=1"));
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("intentional failure"));
  }

  private static final class Broken implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      throw new IllegalStateException("intentional failure");
    }
  }

  @Seeder(dependsOn = Broken.class)
  private static final class Blocked extends Marker {}

  @Seeder(dependsOn = Cyclic.class)
  private static final class Cyclic extends Marker {}

  private int run(List<? extends SeedTask<?>> seeders, String... args) {
    return SeederCli.run(
        args,
        seeders,
        new PrintStream(output, false, StandardCharsets.UTF_8),
        new PrintStream(errors, false, StandardCharsets.UTF_8));
  }

  private static final class Users implements SeedTask<Long> {
    private Thread thread;

    @Override
    public Long run(SeedContext context) {
      thread = Thread.currentThread();
      return context.scaled(3);
    }
  }

  @Seeder(dependsOn = Users.class)
  private static final class Posts implements SeedTask<Void> {
    private long userCount;

    @Override
    public Void run(SeedContext context) {
      userCount = context.get(Users.class);
      return null;
    }
  }

  @Seeder(dependsOn = Users.class)
  private static final class Standalone extends Marker {}

  private static final class Other extends Marker {}

  private static class Marker implements SeedTask<Void> {
    boolean ran;

    @Override
    public Void run(SeedContext context) {
      ran = true;
      return null;
    }
  }

  private static final class Billing {
    private static final class Users extends Marker {}
  }

  private static final class Support {
    private static final class Users extends Marker {}
  }
}
