package io.github.brayandm.seeder;

import io.github.brayandm.seeder.internal.SeedGraph;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Executes explicitly registered seeders. Each call owns its dependency results. */
public final class SeederRunner {
  private final Map<Class<? extends SeedTask<?>>, SeedTask<?>> tasks = new LinkedHashMap<>();
  private static final Consumer<SeederEvent> IGNORE_EVENTS = event -> {};
  private final SeedGraph graph;
  private final Consumer<SeederEvent> listener;

  public SeederRunner(Collection<? extends SeedTask<?>> seeders) {
    this(seeders, IGNORE_EVENTS);
  }

  /**
   * Reports progress on the executing threads. The listener must be thread-safe in parallel mode
   * and must return normally.
   */
  public SeederRunner(Collection<? extends SeedTask<?>> seeders, Consumer<SeederEvent> listener) {
    this.listener = Objects.requireNonNull(listener);
    for (var seeder : seeders) {
      // getClass() erases the task's result type, which is recovered by SeedContext.get.
      @SuppressWarnings("unchecked")
      var type = (Class<? extends SeedTask<?>>) seeder.getClass();
      if (tasks.putIfAbsent(type, seeder) != null) {
        throw new IllegalArgumentException("Duplicate seeder: " + type.getName());
      }
    }
    graph = new SeedGraph(tasks.keySet());
  }

  public void runAll(SeedOptions options) {
    execute(graph.order(), options);
  }

  public void run(Class<? extends SeedTask<?>> target, SeedOptions options) {
    requireRegistered(target);
    execute(graph.order(List.of(target)), options);
  }

  /** Runs one seeder with an empty result context. */
  public void runOnly(Class<? extends SeedTask<?>> target, SeedOptions options) {
    requireRegistered(target);
    invoke(target, Map.of(), options);
  }

  private void requireRegistered(Class<? extends SeedTask<?>> target) {
    if (!tasks.containsKey(target)) {
      throw new IllegalArgumentException("Unknown seeder: " + target.getName());
    }
  }

  private void execute(List<Class<? extends SeedTask<?>>> order, SeedOptions options) {
    Objects.requireNonNull(options);
    if (options.parallel()) {
      executeParallel(order, options);
      return;
    }

    var results = new HashMap<Class<? extends SeedTask<?>>, Object>();
    for (var task : order) {
      var inputs = new HashMap<Class<? extends SeedTask<?>>, Object>();
      for (var dependency : graph.dependencies(task)) {
        inputs.put(dependency, results.get(dependency));
      }
      results.put(task, invoke(task, inputs, options));
    }
  }

  private void executeParallel(List<Class<? extends SeedTask<?>>> order, SeedOptions options) {
    var results = new LinkedHashMap<Class<? extends SeedTask<?>>, CompletableFuture<Object>>();
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (var task : order) {
        var dependencies =
            new LinkedHashMap<Class<? extends SeedTask<?>>, CompletableFuture<Object>>();
        for (var dependency : graph.dependencies(task)) {
          dependencies.put(dependency, results.get(dependency));
        }
        var ready =
            CompletableFuture.allOf(dependencies.values().toArray(CompletableFuture<?>[]::new));
        results.put(
            task,
            ready.thenApplyAsync(
                ignored -> invoke(task, completed(dependencies), options), executor));
      }
      CompletableFuture.allOf(results.values().toArray(CompletableFuture<?>[]::new)).join();
    } catch (CompletionException failure) {
      switch (failure.getCause()) {
        case RuntimeException cause -> throw cause;
        case Error cause -> throw cause;
        default -> throw failure;
      }
    }
  }

  private static Map<Class<? extends SeedTask<?>>, Object> completed(
      Map<Class<? extends SeedTask<?>>, CompletableFuture<Object>> dependencies) {
    var inputs = new HashMap<Class<? extends SeedTask<?>>, Object>();
    dependencies.forEach((task, result) -> inputs.put(task, result.join()));
    return inputs;
  }

  private Object invoke(
      Class<? extends SeedTask<?>> task,
      Map<Class<? extends SeedTask<?>>, Object> inputs,
      SeedOptions options) {
    var context = new SeedContext(task, Set.copyOf(graph.dependencies(task)), inputs, options);
    report(task, SeederEvent.Status.STARTED, Duration.ZERO);
    long started = System.nanoTime();
    var status = SeederEvent.Status.FAILED;
    try {
      var result = tasks.get(task).run(context);
      status = SeederEvent.Status.COMPLETED;
      return result;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Seeder interrupted: " + task.getName(), failure);
    } catch (Exception failure) {
      throw new IllegalStateException("Seeder failed: " + task.getName(), failure);
    } finally {
      report(task, status, Duration.ofNanos(System.nanoTime() - started));
    }
  }

  private void report(
      Class<? extends SeedTask<?>> task, SeederEvent.Status status, Duration elapsed) {
    if (listener != IGNORE_EVENTS) {
      listener.accept(new SeederEvent(task, status, Instant.now(), elapsed));
    }
  }
}
