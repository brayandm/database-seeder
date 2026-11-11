package io.github.brayandm.seeder;

/** Settings shared by all seeders in one run. */
public record SeedOptions(boolean parallel, double scale, long seed) {
  public static final SeedOptions DEFAULT = new SeedOptions(true, 1, 0);

  public SeedOptions {
    if (!Double.isFinite(scale) || scale <= 0) {
      throw new IllegalArgumentException("Scale must be finite and positive");
    }
  }
}
