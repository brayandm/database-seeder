package io.github.brayandm.seeder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ConcurrentRandomTest {
  @Test
  void matchesTheSeededGeneratorForMixedDraws() {
    var expected = new SplittableRandom(42);
    var actual = new ConcurrentRandom(42);
    var expectedBytes = new byte[13];
    var actualBytes = new byte[13];

    assertEquals(expected.nextInt(), actual.nextInt());
    expected.nextBytes(expectedBytes);
    actual.nextBytes(actualBytes);
    assertArrayEquals(expectedBytes, actualBytes);
    assertEquals(expected.nextLong(), actual.nextLong());
  }

  @Test
  void concurrentIntegerDrawsAdvanceTheSequenceOncePerCall() {
    var expected = new SplittableRandom(42).ints(10_000).sorted().toArray();
    var shared = new ConcurrentRandom(42);
    var actual =
        IntStream.range(0, 10_000).parallel().map(i -> shared.nextInt()).sorted().toArray();

    assertArrayEquals(expected, actual);
  }
}
