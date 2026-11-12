package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedOptions;
import java.util.HashSet;

record Arguments(String seeder, boolean only, boolean help, SeedOptions options) {
  static Arguments parse(String[] args) {
    var seen = new HashSet<String>();
    String seeder = null;
    double scale = SeedOptions.DEFAULT.scale();

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
        case "--only", "--linear", "--help" -> {
          if (value != null) {
            throw new IllegalArgumentException(option + " does not take a value");
          }
        }
        default -> throw new IllegalArgumentException("Unknown option: " + option);
      }
    }

    boolean only = seen.contains("--only");
    if (only && seeder == null) {
      throw new IllegalArgumentException("--only requires --seeder");
    }
    var options = new SeedOptions(!seen.contains("--linear"), scale, SeedOptions.DEFAULT.seed());
    return new Arguments(seeder, only, seen.contains("--help"), options);
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
}
