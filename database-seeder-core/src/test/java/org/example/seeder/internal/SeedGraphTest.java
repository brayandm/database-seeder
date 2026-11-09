package org.example.seeder.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.example.seeder.SeedTask;
import org.example.seeder.Seeder;
import org.junit.jupiter.api.Test;

class SeedGraphTest {
  @Test
  void ordersDiamondWithSharedDependencyOnceAndExcludesUnrelatedTasksFromTarget() {
    var graph =
        new SeedGraph(List.of(Target.class, Unrelated.class, Right.class, Left.class, Root.class));

    assertEquals(List.of(Left.class, Right.class), graph.dependencies(Target.class));
    assertEquals(
        List.of(Root.class, Left.class, Right.class, Target.class),
        graph.order(List.of(Target.class)));
    assertEquals(
        List.of(Root.class, Left.class, Right.class, Target.class, Unrelated.class), graph.order());
  }

  @Test
  void reportsMissingTransitiveDependency() {
    var graph = new SeedGraph(List.of(Target.class, Left.class, Right.class));
    var failure = assertThrows(IllegalArgumentException.class, graph::order);

    assertTrue(failure.getMessage().contains(Root.class.getName()));
    assertTrue(failure.getMessage().contains("Target -> Left"));
  }

  @Test
  void reportsCycleEvenWhenReachedThroughAnotherTask() {
    var graph = new SeedGraph(List.of(Entry.class, CycleA.class, CycleB.class));
    var failure = assertThrows(IllegalArgumentException.class, graph::order);

    assertEquals("Circular dependency: CycleA -> CycleB -> CycleA", failure.getMessage());
  }

  @Test
  void rejectsSelfDependency() {
    var graph = new SeedGraph(List.of(Self.class));

    assertEquals(
        "Circular dependency: Self -> Self",
        assertThrows(IllegalArgumentException.class, graph::order).getMessage());
  }

  @Test
  void acceptsAnEmptyGraph() {
    assertTrue(new SeedGraph(List.of()).order().isEmpty());
  }

  private abstract static class Root implements SeedTask<Void> {}

  private abstract static class Unrelated implements SeedTask<Void> {}

  @Seeder(dependsOn = Root.class)
  private abstract static class Left implements SeedTask<Void> {}

  @Seeder(dependsOn = Root.class)
  private abstract static class Right implements SeedTask<Void> {}

  @Seeder(dependsOn = {Left.class, Right.class, Left.class})
  private abstract static class Target implements SeedTask<Void> {}

  @Seeder(dependsOn = CycleA.class)
  private abstract static class Entry implements SeedTask<Void> {}

  @Seeder(dependsOn = CycleB.class)
  private abstract static class CycleA implements SeedTask<Void> {}

  @Seeder(dependsOn = CycleA.class)
  private abstract static class CycleB implements SeedTask<Void> {}

  @Seeder(dependsOn = Self.class)
  private abstract static class Self implements SeedTask<Void> {}
}
