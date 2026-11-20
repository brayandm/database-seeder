package io.github.brayandm.seeder;

/**
 * Settings shared by all seeders in one run.
 *
 * @param parallel whether independent seeders can run concurrently
 * @param scale a finite positive multiplier used by {@link SeedContext#scaled(long)}
 * @param seed the base seed for deterministic random generation
 */
public record SeedOptions(boolean parallel, double scale, long seed) {
  /** Runs ready seeders in parallel at scale 1 with random seed 0. */
  public static final SeedOptions DEFAULT = new SeedOptions(true, 1, 0);

  /**
   * Creates the settings for a run.
   *
   * @throws IllegalArgumentException if scale is non-positive or non-finite
   */
  public SeedOptions {
    if (!Double.isFinite(scale) || scale <= 0) {
      throw new IllegalArgumentException("Scale must be finite and positive");
    }
  }
}
