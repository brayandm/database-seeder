package io.github.brayandm.seeder.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SeederDiscoveryTest {
  private final ByteArrayOutputStream output = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

  @Test
  void executesProviderRegisteredOnTheClasspath(@TempDir Path directory) throws Exception {
    register(directory, Provider.class.getName());

    assertEquals(0, run(directory, "--seeder=CustomerSeeder", "--scale=2"));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Completed CustomerSeeder"));
  }

  @Test
  void reportsMissingProviders(@TempDir Path directory) throws Exception {
    assertEquals(1, run(directory));
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("No SeederProvider found"));
  }

  @Test
  void discoversAnnotatedTasksWithoutAProvider() throws Exception {
    var classes = getClass().getProtectionDomain().getCodeSource().getLocation();
    try (var loader = new URLClassLoader(new URL[] {classes}, getClass().getClassLoader())) {
      assertEquals(
          0,
          SeederCli.runFromClasspath(
              new String[] {"--discover", "--linear", "--scale=2"},
              loader,
              new PrintStream(output, false, StandardCharsets.UTF_8),
              new PrintStream(errors, false, StandardCharsets.UTF_8)));
    }
    var progress = output.toString(StandardCharsets.UTF_8);
    assertTrue(progress.contains("Completed DiscoveredCustomer"));
    assertTrue(progress.contains("Completed DiscoveredOrder"));
    assertEquals(4, progress.lines().count());
  }

  @Test
  void onlyDoesNotConstructAnUnrelatedSeeder() throws Exception {
    var classes = getClass().getProtectionDomain().getCodeSource().getLocation();
    var previous = DiscoveredOrder.rejectConstruction;
    DiscoveredOrder.rejectConstruction = true;
    try (var loader = new URLClassLoader(new URL[] {classes}, getClass().getClassLoader())) {
      assertEquals(
          0,
          SeederCli.runFromClasspath(
              new String[] {"--discover", "--seeder=DiscoveredCustomer", "--only"},
              loader,
              new PrintStream(output, false, StandardCharsets.UTF_8),
              new PrintStream(errors, false, StandardCharsets.UTF_8)));
    } finally {
      DiscoveredOrder.rejectConstruction = previous;
    }
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Completed DiscoveredCustomer"));
  }

  @Test
  void printsHelpWithoutLoadingProviders(@TempDir Path directory) throws Exception {
    register(directory, "missing.Provider");

    assertEquals(0, run(directory, "--help"));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Usage:"));
    assertEquals(1, run(directory));
    assertTrue(errors.toString(StandardCharsets.UTF_8).contains("missing.Provider"));
  }

  private static void register(Path directory, String provider) throws Exception {
    var services = Files.createDirectories(directory.resolve("META-INF/services"));
    Files.writeString(services.resolve(SeederProvider.class.getName()), provider);
  }

  private int run(Path directory, String... args) throws Exception {
    try (var loader =
        new URLClassLoader(
            new URL[] {directory.toUri().toURL()}, SeederProvider.class.getClassLoader())) {
      return SeederCli.runFromClasspath(
          args,
          loader,
          new PrintStream(output, false, StandardCharsets.UTF_8),
          new PrintStream(errors, false, StandardCharsets.UTF_8));
    }
  }

  public static final class Provider implements SeederProvider {
    @Override
    public List<SeedTask<?>> seeders() {
      return List.of(new CustomerSeeder());
    }
  }

  @Seeder
  public static final class DiscoveredCustomer implements SeedTask<Long> {
    @Override
    public Long run(SeedContext context) {
      return context.scaled(10);
    }
  }

  @Seeder(dependsOn = DiscoveredCustomer.class)
  public static final class DiscoveredOrder implements SeedTask<Void> {
    static boolean rejectConstruction;

    public DiscoveredOrder() {
      if (rejectConstruction) {
        throw new IllegalStateException("Unrelated seeder constructed");
      }
    }

    @Override
    public Void run(SeedContext context) {
      assertEquals(20L, context.get(DiscoveredCustomer.class));
      return null;
    }
  }

  @Seeder
  public record NeedsInjection(String repository) implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      throw new AssertionError("Discovery must skip seeders that need constructor injection");
    }
  }

  private static final class CustomerSeeder implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      assertEquals(2, context.scale());
      return null;
    }
  }
}
