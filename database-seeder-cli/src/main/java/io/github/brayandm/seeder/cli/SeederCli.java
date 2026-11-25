package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.SeederEvent;
import io.github.brayandm.seeder.SeederRunner;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
      Usage: [--seeder=<class>] [--only] [--linear] [--scale=<number>] [--verbose] [--discover] [--list | --dry-run] [--help]

        --seeder  Run the named seeder and its dependencies. Accepts a fully
                  qualified class name or an unambiguous simple name.
        --only    Run just the named seeder. Requires --seeder.
        --linear  Run seeders sequentially.
        --scale   Set a finite positive scale. Defaults to 1.
        --verbose Show timestamps, durations, counters, full names and execution settings.
        --discover Discover annotated seeders with public no-argument constructors.
        --list    Print available seeder class names without running them.
        --dry-run Validate dependencies and print the execution plan without running seeders.
        --help    Show this help.
      """;

  private SeederCli() {}

  /**
   * Runs the standalone CLI with classpath discovery or registered providers.
   *
   * @param args command-line options
   */
  public static void main(String[] args) {
    System.exit(
        runFromClasspath(
            args, Thread.currentThread().getContextClassLoader(), System.out, System.err));
  }

  /**
   * Runs registered seeders using standard output and standard error.
   *
   * @param args command-line options
   * @param seeders initialized seeder instances
   * @return 0 for success, 1 for execution failures, or 2 for invalid arguments
   */
  public static int run(String[] args, Collection<? extends SeedTask<?>> seeders) {
    return run(args, seeders, System.out, System.err);
  }

  /**
   * Runs registered seeders using caller-provided output streams.
   *
   * @param args command-line options
   * @param seeders initialized seeder instances
   * @param out stream for help and progress
   * @param err stream for diagnostics
   * @return 0 for success, 1 for execution failures, or 2 for invalid arguments
   */
  public static int run(
      String[] args, Collection<? extends SeedTask<?>> seeders, PrintStream out, PrintStream err) {
    return run(args, seeders, Object::getClass, out, err);
  }

  /**
   * Runs registered seeders identified by their underlying classes.
   *
   * @param args command-line options
   * @param seeders initialized seeder instances, which may be proxies
   * @param seederType function resolving the seeder class used for selection and dependencies
   * @param out stream for help and progress
   * @param err stream for diagnostics
   * @return 0 for success, 1 for execution failures, or 2 for invalid arguments
   */
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

  /**
   * Validates seed options before an embedding application starts its runtime.
   *
   * @param args command-line options for explicitly registered seeders
   * @throws IllegalArgumentException if an option is invalid or requires standalone discovery
   */
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
      var runner =
          source.apply(
              command,
              command.list() || command.dryRun() ? event -> {} : new ConsoleProgress(out, command));
      if (command.list()) {
        runner.seederTypes().stream()
            .map(type -> Objects.requireNonNullElse(type.getCanonicalName(), type.getName()))
            .sorted()
            .forEach(out::println);
        out.flush();
        return 0;
      }
      Class<? extends SeedTask<?>> target;
      try {
        target = command.seeder() == null ? null : resolve(command.seeder(), runner.seederTypes());
      } catch (IllegalArgumentException failure) {
        return usageError(failure, err);
      }

      if (command.dryRun()) {
        var groups =
            target == null
                ? runner.planAll(command.options())
                : runner.plan(target, command.only(), command.options());
        printPlan(groups, command, out);
      } else if (target == null) {
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

  private static void printPlan(
      List<List<Class<? extends SeedTask<?>>>> groups, Arguments command, PrintStream out) {
    out.println(
        "Dry run ("
            + (command.options().parallel() ? "parallel" : "linear")
            + ", scale="
            + command.options().scale()
            + ")");
    if (groups.isEmpty()) {
      out.println("No seeders to run.");
    }
    for (int i = 0; i < groups.size(); i++) {
      var group = groups.get(i);
      var names = group.stream().map(Class::getName).collect(Collectors.joining(", "));
      out.println((i + 1) + ". " + names + (group.size() > 1 ? " (can run in parallel)" : ""));
    }
    out.flush();
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
