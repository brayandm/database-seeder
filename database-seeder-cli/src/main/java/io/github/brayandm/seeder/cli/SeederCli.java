package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.SeederRunner;
import java.io.PrintStream;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

/** Command-line entry point for applications that supply their own seeder instances. */
public final class SeederCli {
  private static final String HELP =
      """
      Usage: [--seeder=<class>] [--only] [--linear] [--scale=<number>] [--verbose] [--help]

        --seeder  Run the named seeder and its dependencies. Accepts a fully
                  qualified class name or an unambiguous simple name.
        --only    Run just the named seeder. Requires --seeder.
        --linear  Run seeders sequentially.
        --scale   Set a finite positive scale. Defaults to 1.
        --verbose Show fully qualified names and execution settings.
        --help    Show this help.
      """;

  private SeederCli() {}

  /** Returns 0 for success, 1 for execution errors, or 2 for invalid arguments. */
  public static int run(String[] args, Collection<? extends SeedTask<?>> seeders) {
    return run(args, seeders, System.out, System.err);
  }

  /** Runs the CLI using caller-provided output streams. */
  public static int run(
      String[] args, Collection<? extends SeedTask<?>> seeders, PrintStream out, PrintStream err) {
    Arguments command;
    Class<? extends SeedTask<?>> target;
    try {
      command = Arguments.parse(args);
      if (command.help()) {
        out.print(HELP);
        out.flush();
        return 0;
      }
      target = command.seeder() == null ? null : resolve(command.seeder(), seeders);
    } catch (IllegalArgumentException failure) {
      err.println(failure.getMessage());
      err.println("Use --help for usage.");
      err.flush();
      return 2;
    }

    var progress = new ConsoleProgress(out, command);
    try {
      var runner = new SeederRunner(seeders, progress);
      if (target == null) {
        runner.runAll(command.options());
      } else if (command.only()) {
        runner.runOnly(target, command.options());
      } else {
        runner.run(target, command.options());
      }
      return 0;
    } catch (RuntimeException failure) {
      failure.printStackTrace(err);
      err.flush();
      return 1;
    }
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
