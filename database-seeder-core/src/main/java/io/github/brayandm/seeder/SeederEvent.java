package io.github.brayandm.seeder;

import java.time.Duration;
import java.time.Instant;

/**
 * Progress for one seeder invocation.
 *
 * @param seeder the registered class of the seeder
 * @param status the current stage or outcome
 * @param timestamp the wall-clock time when the event was emitted
 * @param elapsed time spent in the invocation, or zero for a start event
 */
public record SeederEvent(
    Class<? extends SeedTask<?>> seeder, Status status, Instant timestamp, Duration elapsed) {
  /** Stages reported by the runner. */
  public enum Status {
    /** The runner is about to invoke the seeder. */
    STARTED,
    /** The seeder returned successfully. */
    COMPLETED,
    /** The seeder did not complete successfully. */
    FAILED
  }
}
