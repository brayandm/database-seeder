package io.github.brayandm.seeder;

/**
 * Settings shared by all seeders in one run.
 *
 * @param parallel whether independent seeders can run concurrently
 * @param scale a finite positive multiplier used by {@link SeedContext#scaled(long)}
 * @param seed the base seed for deterministic random generation
 * @param maxParallel maximum number of active seeders in a parallel run
 */
public record SeedOptions(boolean parallel, double scale, long seed, int maxParallel) {
  /** Limit used when no explicit cap is configured. */
  public static final int UNLIMITED = Integer.MAX_VALUE;

  /** Runs ready seeders in parallel at scale 1 with random seed 0. */
  public static final SeedOptions DEFAULT = new SeedOptions(true, 1, 0);

  /**
   * Creates settings with no explicit concurrency cap.
   *
   * @param parallel whether independent seeders can run concurrently
   * @param scale the positive scale factor
   * @param seed the base random seed
   */
  public SeedOptions(boolean parallel, double scale, long seed) {
    this(parallel, scale, seed, UNLIMITED);
  }

  /**
   * Creates the settings for a run.
   *
   * @throws IllegalArgumentException if scale is non-positive or non-finite, or the cap is
   *     non-positive
   */
  public SeedOptions {
    if (!Double.isFinite(scale) || scale <= 0) {
      throw new IllegalArgumentException("Scale must be finite and positive");
    }
    if (maxParallel < 1) {
      throw new IllegalArgumentException("Max parallel must be a positive integer");
    }
  }
}
