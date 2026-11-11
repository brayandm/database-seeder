package io.github.brayandm.seeder.internal;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    var path = new LinkedHashSet<Class<? extends SeedTask<?>>>();
    var visited = new HashSet<Class<? extends SeedTask<?>>>();
    var stack = new ArrayDeque<Frame>();
    for (var task : targets) {
      if (!visited.contains(task)) {
        push(task, path, stack);
      }
      while (!stack.isEmpty()) {
        var frame = stack.getLast();
        if (frame.dependencies().hasNext()) {
          var dependency = frame.dependencies().next();
          if (!visited.contains(dependency)) {
            push(dependency, path, stack);
          }
        } else {
          stack.removeLast();
          path.remove(frame.task());
          visited.add(frame.task());
          order.add(frame.task());
        }
      }
    }
    return order;
  }

  private void push(
      Class<? extends SeedTask<?>> task,
      Set<Class<? extends SeedTask<?>>> path,
      Deque<Frame> stack) {
    if (!edges.containsKey(task)) {
      throw new IllegalArgumentException(
          "Missing seeder: " + task.getName() + " in " + names(path));
    }
    if (!path.add(task)) {
      var route = new ArrayList<>(path);
      var cycle = route.subList(route.indexOf(task), route.size());
      cycle.add(task);
      throw new IllegalArgumentException("Circular dependency: " + names(cycle));
    }

    stack.addLast(new Frame(task, dependencies(task).iterator()));
  }

  private record Frame(
      Class<? extends SeedTask<?>> task, Iterator<Class<? extends SeedTask<?>>> dependencies) {}

  private static String names(Collection<Class<? extends SeedTask<?>>> path) {
    return path.stream().map(Class::getSimpleName).collect(Collectors.joining(" -> "));
  }
}
