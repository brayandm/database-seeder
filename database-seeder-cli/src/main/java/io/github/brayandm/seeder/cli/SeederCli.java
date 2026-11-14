package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.SeederRunner;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
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
    return run(
        args,
        command -> {
          if (command.discover()) {
            throw new IllegalArgumentException("--discover requires the standalone CLI");
          }
          return seeders;
        },
        out,
        err);
  }

  static int runFromClasspath(String[] args, ClassLoader loader, PrintStream out, PrintStream err) {
    return run(
        args,
        command -> command.discover() ? ClasspathSeeders.discover(loader) : discoverSeeders(loader),
        out,
        err);
  }

  private static int run(
      String[] args,
      Function<Arguments, Collection<? extends SeedTask<?>>> source,
      PrintStream out,
      PrintStream err) {
    Arguments command;
    try {
      command = Arguments.parse(args);
    } catch (IllegalArgumentException failure) {
      return usageError(failure, err);
    }
    if (command.help()) {
      out.print(HELP);
      out.flush();
      return 0;
    }

    try {
      var seeders = source.apply(command);
      Class<? extends SeedTask<?>> target;
      try {
        target = command.seeder() == null ? null : resolve(command.seeder(), seeders);
      } catch (IllegalArgumentException failure) {
        return usageError(failure, err);
      }

      var runner = new SeederRunner(seeders, new ConsoleProgress(out, command));
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
      String name, Collection<? extends SeedTask<?>> seeders) {
    var matches = new LinkedHashSet<Class<? extends SeedTask<?>>>();
    for (var seeder : seeders) {
      // A SeedTask instance guarantees that its runtime class implements SeedTask.
      @SuppressWarnings("unchecked")
      var type = (Class<? extends SeedTask<?>>) seeder.getClass();
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
