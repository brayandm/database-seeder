package io.github.brayandm.seeder.spring;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.cli.SeederCli;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.annotation.AnnotatedElementUtils;

/** Launches the consumer's Spring configuration for a single console seeding session. */
public final class SpringSeederCli {
  private static final String APPLICATION = "--application=";

  private SpringSeederCli() {}

  public static void main(String[] args) {
    System.exit(run(args, System.out, System.err));
  }

  static int run(String[] args, PrintStream out, PrintStream err) {
    if (Arrays.asList(args).contains("--help")) {
      out.println("Spring Boot option: --application=<fully qualified application class>");
      return SeederCli.run(new String[] {"--help"}, List.of(), out, err);
    }

    String application = null;
    var springArgs = new ArrayList<String>();
    var seederArgs = new ArrayList<String>();
    for (var arg : args) {
      if (arg.startsWith(APPLICATION)) {
        if (application != null) {
          err.println("--application may only be supplied once");
          return 2;
        }
        application = arg.substring(APPLICATION.length());
      } else if (arg.startsWith("--spring.")) {
        springArgs.add(arg);
      } else {
        seederArgs.add(arg);
      }
    }

    if (application == null || application.isBlank()) {
      err.println("Use --application=<fully qualified application class>");
      return 2;
    }

    var options = seederArgs.toArray(String[]::new);
    try {
      SeederCli.validateEmbeddedArguments(options);
    } catch (IllegalArgumentException failure) {
      err.println(failure.getMessage());
      err.println("Use --help for usage.");
      err.flush();
      return 2;
    }

    try {
      var configuration =
          Class.forName(application, false, Thread.currentThread().getContextClassLoader());
      if (!AnnotatedElementUtils.hasAnnotation(configuration, SpringBootConfiguration.class)) {
        err.println("Expected a Spring Boot configuration class: " + application);
        return 2;
      }
      try (var context =
          new SpringApplicationBuilder(configuration)
              .web(WebApplicationType.NONE)
              .run(springArgs.toArray(String[]::new))) {
        List<SeedTask<?>> seeders =
            context.getBeansOfType(SeedTask.class).values().stream()
                .<SeedTask<?>>map(task -> task)
                .toList();
        if (seeders.isEmpty()) {
          err.println("No SeedTask beans found in " + application);
          return 1;
        }
        return SeederCli.run(options, seeders, AopProxyUtils::ultimateTargetClass, out, err);
      }
    } catch (ClassNotFoundException failure) {
      err.println("Application class not found: " + application);
      return 2;
    } catch (RuntimeException failure) {
      failure.printStackTrace(err);
      return 1;
    }
  }
}
