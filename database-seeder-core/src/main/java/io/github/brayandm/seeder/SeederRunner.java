package io.github.brayandm.seeder;

import io.github.brayandm.seeder.internal.SeedGraph;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Executes explicitly registered seeders. Each call owns its dependency results. Factories and
 * listeners must support concurrent calls during parallel or overlapping runs.
 */
public final class SeederRunner {
  private final Map<Class<? extends SeedTask<?>>, Supplier<? extends SeedTask<?>>> tasks;
  private static final Consumer<SeederEvent> IGNORE_EVENTS = event -> {};
  private final SeedGraph graph;
  private final Consumer<SeederEvent> listener;

  /**
   * Creates a runner for initialized seeders.
   *
   * @param seeders the instances to reuse across runs
   */
  public SeederRunner(Collection<? extends SeedTask<?>> seeders) {
    this(seeders, IGNORE_EVENTS);
  }

  /**
   * Reports progress on the executing threads. The listener must be thread-safe in parallel mode
   * and must return normally.
   *
   * @param seeders the instances to reuse across runs
   * @param listener consumer for progress events
   */
  public SeederRunner(Collection<? extends SeedTask<?>> seeders, Consumer<SeederEvent> listener) {
    this(seeders, listener, Object::getClass);
  }

  /**
   * Registers seeders by their underlying class when a container supplies proxies.
   *
   * @param seeders the initialized instances to invoke
   * @param listener a thread-safe consumer for progress events in parallel mode
   * @param seederType function resolving the class used for dependency metadata
   */
  public SeederRunner(
      Collection<? extends SeedTask<?>> seeders,
      Consumer<SeederEvent> listener,
      Function<SeedTask<?>, Class<?>> seederType) {
    this(asFactories(seeders, seederType), listener);
  }

  /**
   * Registers factories that are invoked only for seeders reached during a run. Each invocation
   * calls its registered factory once.
   *
   * @param factories suppliers keyed by their seeder class, in registration order
   * @param listener a thread-safe consumer for progress events in parallel mode
   * @return the configured runner
   */
  public static SeederRunner withFactories(
      Map<Class<? extends SeedTask<?>>, Supplier<? extends SeedTask<?>>> factories,
      Consumer<SeederEvent> listener) {
    var checked =
        new LinkedHashMap<Class<? extends SeedTask<?>>, Supplier<? extends SeedTask<?>>>();
    factories.forEach((type, factory) -> checked.put(type, () -> type.cast(factory.get())));
    return new SeederRunner(checked, listener);
  }

  private SeederRunner(
      Map<Class<? extends SeedTask<?>>, Supplier<? extends SeedTask<?>>> factories,
      Consumer<SeederEvent> listener) {
    this.tasks = Collections.unmodifiableMap(new LinkedHashMap<>(factories));
    this.listener = Objects.requireNonNull(listener);
    this.graph = new SeedGraph(tasks.keySet());
  }

  /**
   * Lists the seeder classes available for selection.
   *
   * @return an unmodifiable set in registration order
   */
  public Set<Class<? extends SeedTask<?>>> seederTypes() {
    return Collections.unmodifiableSet(tasks.keySet());
  }

  private static Map<Class<? extends SeedTask<?>>, Supplier<? extends SeedTask<?>>> asFactories(
      Collection<? extends SeedTask<?>> seeders, Function<SeedTask<?>, Class<?>> seederType) {
    var factories =
        new LinkedHashMap<Class<? extends SeedTask<?>>, Supplier<? extends SeedTask<?>>>();
    for (var seeder : seeders) {
      // getClass() erases the task's result type, which is recovered by SeedContext.get.
      @SuppressWarnings("unchecked")
      var type = (Class<? extends SeedTask<?>>) seederType.apply(seeder).asSubclass(SeedTask.class);
      if (factories.putIfAbsent(type, () -> seeder) != null) {
        throw new IllegalArgumentException("Duplicate seeder: " + type.getName());
      }
    }
    return factories;
  }

  /**
   * Runs all registered seeders in dependency order.
   *
   * @param options settings for this run
   */
  public void runAll(SeedOptions options) {
    execute(graph.order(), options);
  }

  /**
   * Runs a target and its transitive dependencies.
   *
   * @param target the registered seeder to select
   * @param options settings for this run
   */
  public void run(Class<? extends SeedTask<?>> target, SeedOptions options) {
    requireRegistered(target);
    execute(graph.order(List.of(target)), options);
  }

  /**
   * Runs one seeder with an empty result context.
   *
   * @param target the registered seeder to select
   * @param options settings for this run
   */
  public void runOnly(Class<? extends SeedTask<?>> target, SeedOptions options) {
    requireRegistered(target);
    invoke(target, Map.of(), options);
  }

  /**
   * Plans all registered seeders without invoking their factories or tasks.
   *
   * @param options execution settings
   * @return immutable groups ordered by dependency depth, or one task per group in linear mode
   */
  public List<List<Class<? extends SeedTask<?>>>> planAll(SeedOptions options) {
    return planGroups(graph.order(), options);
  }

  /**
   * Plans a selected seeder without invoking its factory or task.
   *
   * @param target registered seeder to select
   * @param only whether to skip its dependencies
   * @param options execution settings
   * @return immutable groups ordered by dependency depth, or one task per group in linear mode
   */
  public List<List<Class<? extends SeedTask<?>>>> plan(
      Class<? extends SeedTask<?>> target, boolean only, SeedOptions options) {
    requireRegistered(target);
    return planGroups(only ? List.of(target) : graph.order(List.of(target)), options);
  }

  private List<List<Class<? extends SeedTask<?>>>> planGroups(
      List<Class<? extends SeedTask<?>>> order, SeedOptions options) {
    if (!options.parallel() || order.size() < 2) {
      return order.stream().map(task -> List.<Class<? extends SeedTask<?>>>of(task)).toList();
    }

    var depths = new HashMap<Class<? extends SeedTask<?>>, Integer>();
    var groups = new ArrayList<List<Class<? extends SeedTask<?>>>>();
    for (var task : order) {
      int depth = graph.dependencies(task).stream().mapToInt(depths::get).max().orElse(-1) + 1;
      depths.put(task, depth);
      if (depth == groups.size()) {
        groups.add(new ArrayList<>());
      }
      groups.get(depth).add(task);
    }
    return groups.stream().map(List::copyOf).toList();
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
    var slots =
        options.maxParallel() < order.size() ? new Semaphore(options.maxParallel(), true) : null;
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
                ignored -> invokeWithSlot(task, completed(dependencies), options, slots),
                executor));
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

  private Object invokeWithSlot(
      Class<? extends SeedTask<?>> task,
      Map<Class<? extends SeedTask<?>>, Object> inputs,
      SeedOptions options,
      Semaphore slots) {
    if (slots == null) {
      return invoke(task, inputs, options);
    }
    slots.acquireUninterruptibly();
    try {
      return invoke(task, inputs, options);
    } finally {
      slots.release();
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
      var result = tasks.get(task).get().run(context);
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
