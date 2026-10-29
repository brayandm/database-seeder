package org.example.seeder;

/** Produces a result that dependent seeders can read during the same run. */
@FunctionalInterface
public interface SeedTask<R> {
  R run(SeedContext context) throws Exception;
}
