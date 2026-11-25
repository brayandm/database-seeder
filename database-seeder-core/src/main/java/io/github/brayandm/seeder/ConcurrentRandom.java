package io.github.brayandm.seeder;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Serializes access to the state of a seeded generator. */
final class ConcurrentRandom implements RandomGenerator {
  private final SplittableRandom delegate;

  ConcurrentRandom(long seed) {
    delegate = new SplittableRandom(seed);
  }

  @Override
  public synchronized long nextLong() {
    return delegate.nextLong();
  }

  @Override
  public synchronized int nextInt() {
    return delegate.nextInt();
  }

  @Override
  public synchronized void nextBytes(byte[] bytes) {
    delegate.nextBytes(bytes);
  }
}
