package org.example.seeder.internal;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.example.seeder.SeedTask;
import org.example.seeder.Seeder;

public final class SeedGraph {
  private final Map<Class<? extends SeedTask<?>>, List<Class<? extends SeedTask<?>>>> edges =
      new LinkedHashMap<>();

  public SeedGraph(Collection<Class<? extends SeedTask<?>>> tasks) {
    for (var task : tasks) {
      var annotation = task.getAnnotation(Seeder.class);
      edges.put(task, annotation == null ? List.of() : List.of(annotation.dependsOn()));
    }
  }

  public List<Class<? extends SeedTask<?>>> dependencies(Class<? extends SeedTask<?>> task) {
    return edges.get(task).stream().distinct().toList();
  }

  public List<Class<? extends SeedTask<?>>> order() {
    return order(edges.keySet());
  }

  public List<Class<? extends SeedTask<?>>> order(
      Collection<Class<? extends SeedTask<?>>> targets) {
    var order = new ArrayList<Class<? extends SeedTask<?>>>();
    var path = new ArrayList<Class<? extends SeedTask<?>>>();
    var visited = new HashSet<Class<? extends SeedTask<?>>>();
    for (var task : targets) {
      visit(task, path, visited, order);
    }
    return order;
  }

  private void visit(
      Class<? extends SeedTask<?>> task,
      List<Class<? extends SeedTask<?>>> path,
      Set<Class<? extends SeedTask<?>>> visited,
      List<Class<? extends SeedTask<?>>> order) {
    if (visited.contains(task)) {
      return;
    }
    if (!edges.containsKey(task)) {
      throw new IllegalArgumentException(
          "Missing seeder: " + task.getName() + " in " + names(path));
    }
    int cycleStart = path.indexOf(task);
    if (cycleStart >= 0) {
      var cycle = new ArrayList<>(path.subList(cycleStart, path.size()));
      cycle.add(task);
      throw new IllegalArgumentException("Circular dependency: " + names(cycle));
    }

    path.add(task);
    for (var dependency : dependencies(task)) {
      visit(dependency, path, visited, order);
    }
    path.removeLast();
    visited.add(task);
    order.add(task);
  }

  private static String names(List<Class<? extends SeedTask<?>>> path) {
    return path.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "));
  }
}
