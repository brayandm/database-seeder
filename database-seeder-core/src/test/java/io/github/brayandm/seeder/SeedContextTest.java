package io.github.brayandm.seeder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeedContextTest {
  @Test
  void readsTypedResultsAndDistinguishesNullFromMissing() {
    var results = new HashMap<Class<? extends SeedTask<?>>, Object>();
    results.put(Producer.class, List.of(42));
    results.put(Empty.class, null);
    var context =
        new SeedContext(
            Consumer.class, Set.of(Producer.class, Empty.class), results, SeedOptions.DEFAULT);

    List<Integer> values = context.get(Producer.class);
    assertEquals(List.of(42), values);
    assertNull(context.get(Empty.class));
  }

  @Test
  void requiresBothDeclarationAndCompletedResult() {
    var context =
        new SeedContext(
            Consumer.class,
            Set.of(Empty.class),
            Map.of(Producer.class, List.of(42)),
            SeedOptions.DEFAULT);

    assertThrows(IllegalArgumentException.class, () -> context.get(Producer.class));
    assertThrows(IllegalStateException.class, () -> context.get(Empty.class));
  }

  @Test
  void roundsScaledCountsAndReplaysRandomStream() {
    var options = new SeedOptions(false, 1.5, 43);
    var first = new SeedContext(Consumer.class, Set.of(), Map.of(), options);
    var second = new SeedContext(Consumer.class, Set.of(), Map.of(), options);

    assertEquals(1.5, first.scale());
    assertEquals(5, first.scaled(3));
    assertEquals(0, first.scaled(0));
    assertArrayEquals(first.random().longs(16).toArray(), second.random().longs(16).toArray());
  }

  @ParameterizedTest
  @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
  void rejectsInvalidScales(double scale) {
    assertThrows(IllegalArgumentException.class, () -> new SeedOptions(false, scale, 0));
  }

  private abstract static class Producer implements SeedTask<List<Integer>> {}

  private abstract static class Empty implements SeedTask<Void> {}

  private abstract static class Consumer implements SeedTask<Void> {}
}
