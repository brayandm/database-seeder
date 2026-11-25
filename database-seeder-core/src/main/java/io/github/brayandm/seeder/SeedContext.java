package io.github.brayandm.seeder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Holds a read-only snapshot of one task's dependency bindings and run settings. */
public final class SeedContext {
  private final Set<Class<? extends SeedTask<?>>> dependencies;
  private final Map<Class<? extends SeedTask<?>>, Object> results;
  private final double scale;
  private final long randomSeed;
  private final RandomGenerator random;

  SeedContext(
      Class<? extends SeedTask<?>> task,
      Set<Class<? extends SeedTask<?>>> dependencies,
      Map<Class<? extends SeedTask<?>>, Object> results,
      SeedOptions options) {
    this.dependencies = Set.copyOf(dependencies);
    // Null is a valid result, so Map.copyOf cannot be used here.
    this.results = Collections.unmodifiableMap(new HashMap<>(results));
    this.scale = options.scale();
    this.randomSeed = options.seed() ^ task.getName().hashCode();
    this.random = new ConcurrentRandom(randomSeed);
  }

  /**
   * Reads the result of a declared direct dependency from this run. The result object is shared
   * with other dependents. Publish immutable results or coordinate access to mutable results.
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
   * Provides a thread-safe random generator for this seeder invocation. With concurrent callers,
   * scheduling determines which caller receives each value.
   *
   * @return the random generator owned by this invocation
   */
  public RandomGenerator random() {
    return random;
  }

  /**
   * Creates a fresh deterministic generator for a unit of work within this invocation. Use stable,
   * distinct IDs to reproduce results regardless of job scheduling. Calling this method again with
   * the same ID restarts that sequence.
   *
   * @param streamId stable identifier for the unit of work
   * @return a thread-safe generator for the requested sequence
   */
  public RandomGenerator random(long streamId) {
    return new ConcurrentRandom(randomSeed ^ new SplittableRandom(streamId).nextLong());
  }
}
