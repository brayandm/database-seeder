package io.github.brayandm.seeder.spring;

import io.github.brayandm.seeder.cli.SeederCli;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Launches the consumer's Spring configuration for a single console seeding session. */
public final class SpringSeederCli {
  private static final String APPLICATION = "--application=";

  private SpringSeederCli() {}

  /**
   * Starts a console Spring context and runs the requested seeders.
   *
   * @param args application and seeder command-line options
   */
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
      try (var context = consoleApplication(configuration, springArgs)) {
        var seeders = SpringSeeders.collect(context.getBeanFactory());
        if (seeders.isEmpty() && !seederArgs.contains("--list")) {
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

  private static ConfigurableApplicationContext consoleApplication(
      Class<?> configuration, List<String> springArgs) {
    var args = springArgs.toArray(String[]::new);
    var environment = new StandardEnvironment();
    if (args.length > 0) {
      environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
    }
    environment
        .getPropertySources()
        .addFirst(
            new MapPropertySource(
                "databaseSeederConsole", Map.of("spring.main.web-application-type", "none")));

    var application =
        new SpringApplicationBuilder(configuration)
            .environment(environment)
            .web(WebApplicationType.NONE)
            .build();
    // The command-line properties are already present below the console override.
    application.setAddCommandLineProperties(false);
    return application.run(args);
  }
}
