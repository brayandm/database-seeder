package io.github.brayandm.seeder;

import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Gives one seed task access to its completed direct dependencies and run settings. */
public final class SeedContext {
  private final Set<Class<? extends SeedTask<?>>> dependencies;
  private final Map<Class<? extends SeedTask<?>>, Object> results;
  private final double scale;
  private final RandomGenerator random;

  SeedContext(
      Class<? extends SeedTask<?>> task,
      Set<Class<? extends SeedTask<?>>> dependencies,
      Map<Class<? extends SeedTask<?>>, Object> results,
      SeedOptions options) {
    this.dependencies = dependencies;
    this.results = results;
    this.scale = options.scale();
    this.random = new SplittableRandom(options.seed() ^ task.getName().hashCode());
  }

  /**
   * Reads the result of a declared direct dependency from this run.
   *
   * @param <R> the result type of the dependency
   * @param dependency the class that produced the result
   * @return the dependency result, which may be null
   * @throws IllegalArgumentException if the dependency was not declared
   * @throws IllegalStateException if the dependency has no result in this run
   */
  public <R> R get(Class<? extends SeedTask<R>> dependency) {
    if (!dependencies.contains(dependency)) {
      throw new IllegalArgumentException("Undeclared dependency: " + dependency.getName());
    }
    if (!results.containsKey(dependency)) {
      throw new IllegalStateException(
          "Dependency has no result in this run: " + dependency.getName());
    }
    // The runner stores each result under the class of the task that produced it.
    @SuppressWarnings("unchecked")
    R result = (R) results.get(dependency);
    return result;
  }

  /**
   * Returns the multiplier configured for this run.
   *
   * @return the scale factor
   */
  public double scale() {
    return scale;
  }

  /**
   * Applies the scale factor and rounds with {@link Math#round(double)}.
   *
   * @param count the unscaled count
   * @return the scaled and rounded count
   */
  public long scaled(long count) {
    return Math.round(count * scale);
  }

  /**
   * Provides deterministic random values for this seeder invocation.
   *
   * @return the random generator owned by this invocation
   */
  public RandomGenerator random() {
    return random;
  }
}
