package io.github.brayandm.seeder;

import java.time.Duration;
import java.time.Instant;

/** Progress for a seeder invocation. Elapsed time is zero for a start event. */
public record SeederEvent(
    Class<? extends SeedTask<?>> seeder, Status status, Instant timestamp, Duration elapsed) {
  public enum Status {
    STARTED,
    COMPLETED,
    FAILED
  }
}
