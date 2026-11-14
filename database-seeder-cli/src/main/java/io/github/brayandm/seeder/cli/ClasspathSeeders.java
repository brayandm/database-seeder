package io.github.brayandm.seeder.cli;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

final class ClasspathSeeders {
  private ClasspathSeeders() {}

  static Collection<SeedTask<?>> discover(ClassLoader loader) {
    var names = new TreeSet<String>();
    try {
      for (var entry : entries(loader)) {
        if (Files.isDirectory(entry)) {
          try (var files = Files.walk(entry)) {
            files
                .filter(Files::isRegularFile)
                .map(entry::relativize)
                .forEach(file -> addName(names, file.toString().replace(File.separatorChar, '/')));
          }
        } else if (Files.isRegularFile(entry)) {
          try (var jar = new JarFile(entry.toFile(), false, JarFile.OPEN_READ, Runtime.version())) {
            jar.versionedStream().forEach(file -> addName(names, file.getName()));
          }
        }
      }
    } catch (IOException | URISyntaxException failure) {
      throw new IllegalStateException("Unable to scan the runtime classpath", failure);
    }

    var seeders = new ArrayList<SeedTask<?>>();
    for (var name : names) {
      var type = loadClass(name, loader);
      if (type == null
          || !SeedTask.class.isAssignableFrom(type)
          || !type.isAnnotationPresent(Seeder.class)
          || Modifier.isAbstract(type.getModifiers())
          || !Modifier.isPublic(type.getModifiers())) {
        continue;
      }
      try {
        seeders.add((SeedTask<?>) type.getConstructor().newInstance());
      } catch (NoSuchMethodException ignored) {
        // This seeder must be constructed by the application or a dependency injection container.
      } catch (ReflectiveOperationException failure) {
        throw new IllegalStateException("Unable to instantiate seeder: " + name, failure);
      }
    }
    if (seeders.isEmpty()) {
      throw new IllegalStateException(
          "No public @Seeder classes implementing SeedTask with a public no-argument constructor found");
    }
    return seeders;
  }

  private static Set<Path> entries(ClassLoader loader) throws URISyntaxException {
    var entries = new LinkedHashSet<Path>();
    Arrays.stream(
            System.getProperty("java.class.path", "").split(Pattern.quote(File.pathSeparator)))
        .map(Path::of)
        .forEach(entries::add);
    for (var current = loader; current != null; current = current.getParent()) {
      if (current instanceof URLClassLoader urls) {
        for (var url : urls.getURLs()) {
          if (url.getProtocol().equals("file")) {
            entries.add(Path.of(url.toURI()));
          }
        }
      }
    }
    return entries;
  }

  private static void addName(Set<String> names, String entry) {
    if (entry.endsWith(".class")
        && !entry.startsWith("META-INF/")
        && !entry.endsWith("module-info.class")
        && !entry.endsWith("package-info.class")) {
      names.add(entry.substring(0, entry.length() - ".class".length()).replace('/', '.'));
    }
  }

  private static Class<?> loadClass(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException | LinkageError ignored) {
      // Dependencies may contain classes for optional runtimes absent from this classpath.
      return null;
    }
  }
}
