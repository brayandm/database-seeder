package io.github.brayandm.seeder.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brayandm.seeder.SeedOptions;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.SeederRunner;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeedGraphStressTest {
  private static final int COUNT = 10_000;
  private static final int ROOT_COUNT = 32;
  private static final long SEED = 0x5EEDL;
  private static final String GENERATED_CLASS = "GeneratedSeeders";

  @Test
  void sortsAndRunsShuffledSeeders(@TempDir Path directory) throws Exception {
    var dependencies = generateDependencies();
    compileSeeders(directory, dependencies);

    try (var loader =
        new URLClassLoader(
            new URL[] {directory.toUri().toURL()}, SeedTask.class.getClassLoader())) {
      var types = loadTypes(loader, COUNT);
      var shuffled = new ArrayList<>(types);
      Collections.shuffle(shuffled, new Random(SEED + 1));
      assertTopologicalOrder(types, shuffled, dependencies);
      assertExecution(shuffled);
    }
  }

  @Test
  void sortsASeederChain(@TempDir Path directory) throws Exception {
    var dependencies = new ArrayList<List<Integer>>(COUNT);
    dependencies.add(List.of());
    for (int id = 1; id < COUNT; id++) {
      dependencies.add(List.of(id - 1));
    }
    compileSeeders(directory, dependencies);

    try (var loader =
        new URLClassLoader(
            new URL[] {directory.toUri().toURL()}, SeedTask.class.getClassLoader())) {
      var types = loadTypes(loader, COUNT);
      assertEquals(types, new SeedGraph(types.reversed()).order());
    }
  }

  private static List<Class<? extends SeedTask<?>>> loadTypes(URLClassLoader loader, int count)
      throws ClassNotFoundException {
    var types = new ArrayList<Class<? extends SeedTask<?>>>(count);
    for (int id = 0; id < count; id++) {
      // All generated classes implement SeedTask<Long>.
      @SuppressWarnings("unchecked")
      var type =
          (Class<? extends SeedTask<?>>)
              loader.loadClass(GENERATED_CLASS + "$Node" + id).asSubclass(SeedTask.class);
      types.add(type);
    }
    return types;
  }

  private static List<List<Integer>> generateDependencies() {
    var random = new Random(SEED);
    var dependencies = new ArrayList<List<Integer>>(COUNT);
    for (int id = 0; id < COUNT; id++) {
      if (id < ROOT_COUNT) {
        dependencies.add(List.of());
      } else {
        dependencies.add(
            random.ints(0, id).distinct().limit(random.nextInt(2, 9)).boxed().toList());
      }
    }
    return dependencies;
  }

  private static void assertTopologicalOrder(
      List<Class<? extends SeedTask<?>>> types,
      List<Class<? extends SeedTask<?>>> shuffled,
      List<List<Integer>> dependencies) {
    var ordered = new SeedGraph(shuffled).order();
    assertEquals(COUNT, ordered.size());
    var positions = new HashMap<Class<? extends SeedTask<?>>, Integer>();
    for (int index = 0; index < ordered.size(); index++) {
      positions.put(ordered.get(index), index);
    }
    assertEquals(COUNT, positions.size());
    assertTrue(positions.keySet().containsAll(types));

    for (int id = 0; id < COUNT; id++) {
      for (int dependency : dependencies.get(id)) {
        assertTrue(
            positions.get(types.get(dependency)) < positions.get(types.get(id)),
            "Node%d must precede Node%d".formatted(dependency, id));
      }
    }
  }

  private static void assertExecution(List<Class<? extends SeedTask<?>>> shuffled)
      throws Exception {
    var executions =
        List.of(
            new AtomicIntegerArray(COUNT),
            new AtomicIntegerArray(COUNT),
            new AtomicIntegerArray(COUNT));
    BiConsumer<Integer, Integer> recordExecution =
        (id, scale) -> executions.get(scale - 1).incrementAndGet(id);

    var tasks = new ArrayList<SeedTask<?>>(COUNT);
    for (var type : shuffled) {
      tasks.add(type.getConstructor(BiConsumer.class).newInstance(recordExecution));
    }
    var runner = new SeederRunner(tasks);
    runner.runAll(new SeedOptions(false, 1, SEED));
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> runner.runAll(new SeedOptions(true, 2, SEED)));
      var second = executor.submit(() -> runner.runAll(new SeedOptions(true, 3, SEED)));
      first.get();
      second.get();
    }
    for (var counts : executions) {
      for (int id = 0; id < COUNT; id++) {
        assertEquals(1, counts.get(id), "Execution count for Node" + id);
      }
    }
  }

  private static void compileSeeders(Path directory, List<List<Integer>> dependencies)
      throws Exception {
    // Each node needs a distinct class because the production graph uses Class keys.
    var source =
        new StringBuilder(
            """
                                import java.util.function.BiConsumer;
                                import io.github.brayandm.seeder.SeedContext;
                                import io.github.brayandm.seeder.SeedTask;
                                import io.github.brayandm.seeder.Seeder;

                                public class GeneratedSeeders {
                                """);
    for (int id = 0; id < dependencies.size(); id++) {
      var parents = dependencies.get(id);
      String classes =
          parents.stream()
              .map(parent -> "Node" + parent + ".class")
              .collect(Collectors.joining(", "));
      source.append(
          """
                            @Seeder(dependsOn = {%s})
                            public static final class Node%d extends Task {
                              public Node%d(BiConsumer<Integer, Integer> recorder) {
                                super(%d, recorder);
                              }
                            }
                            """
              .formatted(classes, id, id, id));
    }
    source.append(
        """
                          public abstract static class Task implements SeedTask<Long> {
                            private final int id;
                            private final BiConsumer<Integer, Integer> recorder;

                            Task(int id, BiConsumer<Integer, Integer> recorder) {
                              this.id = id;
                              this.recorder = recorder;
                            }

                            @Override
                            public Long run(SeedContext context) {
                              for (var dependency : getClass().getAnnotation(Seeder.class).dependsOn()) {
                                long expected = context.scaled(nodeId(dependency));
                                Long actual = context.get(dependency.asSubclass(Task.class));
                                if (!Long.valueOf(expected).equals(actual)) {
                                  throw new AssertionError("Wrong result from " + dependency.getName());
                                }
                              }
                              recorder.accept(id, (int) context.scale());
                              return context.scaled(id);
                            }

                            private static int nodeId(Class<?> type) {
                              return Integer.parseInt(type.getSimpleName().substring("Node".length()));
                            }
                          }
                        }
                        """);
    var file = Files.writeString(directory.resolve(GENERATED_CLASS + ".java"), source);
    var classPath =
        Path.of(SeedTask.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    var errors = new ByteArrayOutputStream();
    int status =
        ToolProvider.getSystemJavaCompiler()
            .run(
                null,
                null,
                errors,
                "-proc:none",
                "-classpath",
                classPath.toString(),
                "-d",
                directory.toString(),
                file.toString());
    assertEquals(0, status, () -> errors.toString(StandardCharsets.UTF_8));
  }
}
