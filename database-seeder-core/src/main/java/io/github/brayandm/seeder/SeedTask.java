package io.github.brayandm.seeder;

/**
 * Produces a result that dependent seeders can read during the same run.
 *
 * @param <R> the result type, or {@link Void} for a task with no result
 */
@FunctionalInterface
public interface SeedTask<R> {
  /**
   * Performs this seeding task.
   *
   * @param context results of declared dependencies and settings for this invocation
   * @return the value available to dependents, which may be null
   * @throws Exception if the task fails
   */
  R run(SeedContext context) throws Exception;
}
