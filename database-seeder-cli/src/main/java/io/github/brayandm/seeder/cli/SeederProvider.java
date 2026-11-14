package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedTask;
import java.util.Collection;

/**
 * Supplies initialized seeders to the standalone CLI. On the classpath, register a public
 * implementation with a public no-argument constructor in {@code
 * META-INF/services/io.github.brayandm.seeder.cli.SeederProvider}.
 */
public interface SeederProvider {
  Collection<? extends SeedTask<?>> seeders();
}
