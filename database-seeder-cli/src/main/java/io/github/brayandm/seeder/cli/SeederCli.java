package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.SeederEvent;
import io.github.brayandm.seeder.SeederRunner;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Command-line entry point for applications that supply their own seeder instances. */
public final class SeederCli {
  private static final String HELP =
      """
      Usage: [--seeder=<class>] [--only] [--linear] [--scale=<number>] [--verbose] [--discover] [--help]

        --seeder  Run the named seeder and its dependencies. Accepts a fully
                  qualified class name or an unambiguous simple name.
        --only    Run just the named seeder. Requires --seeder.
        --linear  Run seeders sequentially.
        --scale   Set a finite positive scale. Defaults to 1.
        --verbose Show timestamps, durations, counters, full names and execution settings.
        --discover Discover annotated seeders with public no-argument constructors.
        --help    Show this help.
      """;

  private SeederCli() {}

  /** Runs the standalone CLI with providers discovered on the runtime classpath. */
  public static void main(String[] args) {
    System.exit(
        runFromClasspath(
            args, Thread.currentThread().getContextClassLoader(), System.out, System.err));
  }

  /** Returns 0 for success, 1 for execution errors, or 2 for invalid arguments. */
  public static int run(String[] args, Collection<? extends SeedTask<?>> seeders) {
    return run(args, seeders, System.out, System.err);
  }

  /** Runs the CLI using caller-provided output streams. */
  public static int run(
      String[] args, Collection<? extends SeedTask<?>> seeders, PrintStream out, PrintStream err) {
    return run(args, seeders, Object::getClass, out, err);
  }

  /** Uses the underlying seeder classes when the caller supplies proxy instances. */
  public static int run(
      String[] args,
      Collection<? extends SeedTask<?>> seeders,
      Function<SeedTask<?>, Class<?>> seederType,
      PrintStream out,
      PrintStream err) {
    return run(
        args,
        (command, progress) -> new SeederRunner(seeders, progress, seederType),
        false,
        out,
        err);
  }

  /** Validates seed options before an embedding application starts its runtime. */
  public static void validateEmbeddedArguments(String[] args) {
    parse(args, false);
  }

  static int runFromClasspath(String[] args, ClassLoader loader, PrintStream out, PrintStream err) {
    return run(
        args,
        (command, progress) ->
            command.discover()
                ? SeederRunner.withFactories(ClasspathSeeders.discover(loader), progress)
                : new SeederRunner(discoverSeeders(loader), progress),
        true,
        out,
        err);
  }

  private static Arguments parse(String[] args, boolean standalone) {
    var command = Arguments.parse(args);
    if (command.discover() && !standalone) {
      throw new IllegalArgumentException("--discover requires the standalone CLI");
    }
    return command;
  }

  private static int run(
      String[] args,
      BiFunction<Arguments, Consumer<SeederEvent>, SeederRunner> source,
      boolean standalone,
      PrintStream out,
      PrintStream err) {
    Arguments command;
    try {
      command = parse(args, standalone);
    } catch (IllegalArgumentException failure) {
      return usageError(failure, err);
    }
    if (command.help()) {
      out.print(HELP);
      out.flush();
      return 0;
    }

    try {
      var runner = source.apply(command, new ConsoleProgress(out, command));
      Class<? extends SeedTask<?>> target;
      try {
        target = command.seeder() == null ? null : resolve(command.seeder(), runner.seederTypes());
      } catch (IllegalArgumentException failure) {
        return usageError(failure, err);
      }

      if (target == null) {
        runner.runAll(command.options());
      } else if (command.only()) {
        runner.runOnly(target, command.options());
      } else {
        runner.run(target, command.options());
      }
      return 0;
    } catch (RuntimeException | ServiceConfigurationError failure) {
      failure.printStackTrace(err);
      err.flush();
      return 1;
    }
  }

  private static Collection<SeedTask<?>> discoverSeeders(ClassLoader loader) {
    var providers = ServiceLoader.load(SeederProvider.class, loader).iterator();
    if (!providers.hasNext()) {
      throw new IllegalStateException(
          "No SeederProvider found. Register an implementation in META-INF/services/"
              + SeederProvider.class.getName());
    }

    var seeders = new ArrayList<SeedTask<?>>();
    while (providers.hasNext()) {
      seeders.addAll(providers.next().seeders());
    }
    return seeders;
  }

  private static int usageError(IllegalArgumentException failure, PrintStream err) {
    err.println(failure.getMessage());
    err.println("Use --help for usage.");
    err.flush();
    return 2;
  }

  private static Class<? extends SeedTask<?>> resolve(
      String name, Collection<Class<? extends SeedTask<?>>> types) {
    var matches = new LinkedHashSet<Class<? extends SeedTask<?>>>();
    for (var type : types) {
      if (type.getName().equals(name) || name.equals(type.getCanonicalName())) {
        return type;
      }
      if (type.getSimpleName().equals(name)) {
        matches.add(type);
      }
    }
    if (matches.isEmpty()) {
      throw new IllegalArgumentException("Unknown seeder: " + name);
    }
    if (matches.size() > 1) {
      var choices = matches.stream().map(Class::getName).sorted().collect(Collectors.joining(", "));
      throw new IllegalArgumentException("Ambiguous seeder: " + name + ". Choose from: " + choices);
    }
    return matches.getFirst();
  }
}
