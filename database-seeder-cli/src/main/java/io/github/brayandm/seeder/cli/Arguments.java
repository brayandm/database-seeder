package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedOptions;
import java.util.HashSet;

record Arguments(
    String seeder,
    boolean only,
    boolean help,
    boolean verbose,
    boolean discover,
    boolean list,
    boolean dryRun,
    SeedOptions options) {
  static Arguments parse(String[] args) {
    var seen = new HashSet<String>();
    String seeder = null;
    double scale = SeedOptions.DEFAULT.scale();
    int maxParallel = SeedOptions.UNLIMITED;

    for (var arg : args) {
      int separator = arg.indexOf('=');
      String option = separator < 0 ? arg : arg.substring(0, separator);
      String value = separator < 0 ? null : arg.substring(separator + 1);
      if (!seen.add(option)) {
        throw new IllegalArgumentException("Duplicate option: " + option);
      }

      switch (option) {
        case "--seeder" -> seeder = requireValue(option, value);
        case "--scale" -> scale = parseScale(requireValue(option, value));
        case "--max-parallel" -> maxParallel = parseMaxParallel(requireValue(option, value));
        case "--only", "--linear", "--help", "--verbose", "--discover", "--list", "--dry-run" -> {
          if (value != null) {
            throw new IllegalArgumentException(option + " does not take a value");
          }
        }
        default -> throw new IllegalArgumentException("Unknown option: " + option);
      }
    }

    boolean only = seen.contains("--only");
    boolean list = seen.contains("--list");
    boolean dryRun = seen.contains("--dry-run");
    if (list && dryRun) {
      throw new IllegalArgumentException("--list cannot be combined with --dry-run");
    }
    if (list && (seeder != null || only)) {
      throw new IllegalArgumentException("--list cannot be combined with --seeder or --only");
    }
    if (only && seeder == null) {
      throw new IllegalArgumentException("--only requires --seeder");
    }
    boolean linear = seen.contains("--linear");
    if (linear && seen.contains("--max-parallel")) {
      throw new IllegalArgumentException("--linear cannot be combined with --max-parallel");
    }
    var options = new SeedOptions(!linear, scale, SeedOptions.DEFAULT.seed(), maxParallel);
    return new Arguments(
        seeder,
        only,
        seen.contains("--help"),
        seen.contains("--verbose"),
        seen.contains("--discover"),
        list,
        dryRun,
        options);
  }

  private static String requireValue(String option, String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(option + " requires a value");
    }
    return value;
  }

  private static double parseScale(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException("Invalid --scale: " + value, failure);
    }
  }

  private static int parseMaxParallel(String value) {
    try {
      int limit = Integer.parseInt(value);
      if (limit > 0) {
        return limit;
      }
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException(
          "--max-parallel requires a positive integer: " + value, failure);
    }
    throw new IllegalArgumentException("--max-parallel requires a positive integer: " + value);
  }
}
