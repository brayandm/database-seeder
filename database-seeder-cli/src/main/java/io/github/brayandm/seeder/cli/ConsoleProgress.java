package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeederEvent;
import java.io.PrintStream;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Consumer;

final class ConsoleProgress implements Consumer<SeederEvent> {
  private final PrintStream out;
  private final boolean verbose;
  private int started;
  private int completed;
  private int failed;

  ConsoleProgress(PrintStream out, Arguments command) {
    this.out = out;
    this.verbose = command.verbose();
    if (verbose) {
      out.printf("[%s] Starting seeders%n", Instant.now());
      var options = command.options();
      var target = command.seeder() == null ? "all" : command.seeder();
      out.printf(
          Locale.ROOT,
          "  mode=%s scale=%s seed=%d target=%s only=%b%n",
          options.parallel() ? "parallel" : "sequential",
          options.scale(),
          options.seed(),
          target,
          command.only());
      out.flush();
    }
  }

  @Override
  public synchronized void accept(SeederEvent event) {
    var type = event.seeder();
    var name = verbose || type.getSimpleName().isEmpty() ? type.getName() : type.getSimpleName();
    var status =
        switch (event.status()) {
          case STARTED -> {
            started++;
            yield "Started";
          }
          case COMPLETED -> {
            completed++;
            yield "Completed";
          }
          case FAILED -> {
            failed++;
            yield "Failed";
          }
        };
    if (verbose) {
      var message = status + " " + name;
      if (event.status() != SeederEvent.Status.STARTED) {
        double milliseconds = event.elapsed().toNanos() / 1_000_000.0;
        message += String.format(Locale.ROOT, " in %.3f ms", milliseconds);
      }
      out.printf(
          "[%s] %s (started=%d, completed=%d, failed=%d)%n",
          event.timestamp(), message, started, completed, failed);
    } else {
      out.println(status + " " + name);
    }
    out.flush();
  }
}
