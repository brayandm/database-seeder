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
    assertTrue(progress.contains("Completed Posts in "));
    assertTrue(progress.contains("started=3, completed=3, failed=0"));
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
        "--unknown"
      })
  void rejectsInvalidArgumentsBeforeRunning(String argument) {
    var other = new Other();

    assertEquals(2, run(List.of(other), argument));
    assertFalse(other.ran);
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("--help"));
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
  void flushesProgressBeforeTheSeederFinishes() {
    var buffered = new PrintStream(new BufferedOutputStream(output), false, StandardCharsets.UTF_8);
    SeedTask<Void> task =
        context -> {
          var progress = output.toString(StandardCharsets.UTF_8);
          assertTrue(progress.contains("started=1, completed=0, failed=0"));
          assertFalse(progress.contains("Completed "));
          return null;
        };

    assertEquals(0, SeederCli.run(new String[0], List.of(task), buffered, System.err));
    assertTrue(
        output.toString(StandardCharsets.UTF_8).contains("started=1, completed=1, failed=0"));
  }

  @Test
  void reportsFailedSeedersAndVerboseDetails() {
    assertEquals(1, run(List.of(new Broken(), new Blocked()), "--verbose", "--linear"));

    var progress = output.toString(StandardCharsets.UTF_8);
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
