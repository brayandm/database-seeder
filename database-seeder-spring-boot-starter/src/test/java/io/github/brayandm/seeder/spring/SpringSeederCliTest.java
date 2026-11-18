package io.github.brayandm.seeder.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brayandm.seeder.SeedOptions;
import io.github.brayandm.seeder.SeederRunner;
import io.github.brayandm.seeder.spring.fixture.TestApplication;
import io.github.brayandm.seeder.spring.fixture.TestApplication.Orders;
import io.github.brayandm.seeder.spring.fixture.TestApplication.Repository;
import io.github.brayandm.seeder.spring.jpa.JpaTestApplication;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

class SpringSeederCliTest {
  @Test
  void registersSeedersWithInjectedDependenciesAndKeepsProxyTypesTransparent() {
    try (var context =
        new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE).run()) {
      var repository = context.getBean(Repository.class);
      assertEquals(0, repository.orders());

      context.getBean(SeederRunner.class).run(Orders.class, new SeedOptions(false, 2, 0));
      assertEquals(6, repository.orders());
    }
  }

  @Test
  void invokesTheSharedCliWithoutAnApplicationMain() {
    var output = new ByteArrayOutputStream();
    var errors = new ByteArrayOutputStream();

    int status =
        SpringSeederCli.run(
            new String[] {
              "--application=" + TestApplication.class.getName(), "--seeder=Orders", "--scale=2"
            },
            new PrintStream(output, false, StandardCharsets.UTF_8),
            new PrintStream(errors, false, StandardCharsets.UTF_8));

    assertEquals(0, status, () -> errors.toString(StandardCharsets.UTF_8));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("Completed Orders"));
  }

  @Test
  void invalidArgumentsDoNotStartTheApplication() {
    int before = StartupApplication.starts.get();

    assertEquals(2, run("--application=" + StartupApplication.class.getName(), "--scale=invalid"));
    assertEquals(before, StartupApplication.starts.get());
  }

  @Test
  void rejectsNonSpringBootSourcesAndEmptyApplications() {
    assertEquals(2, run("--application=java.lang.String"));
    assertEquals(1, run("--application=" + StartupApplication.class.getName()));
  }

  @Test
  void discoveryUsesTheActiveApplicationProfile() {
    String application = "--application=" + TestApplication.class.getName();
    assertEquals(2, run(application, "--seeder=ProfileSeeder"));
    assertEquals(
        0, run(application, "--seeder=ProfileSeeder", "--spring.profiles.active=seed-test"));
  }

  @Test
  void runsTransactionalSeederWithRealJpaRepository() {
    assertEquals(
        0,
        run("--application=" + JpaTestApplication.class.getName(), "--seeder=Orders", "--scale=2"));
  }

  private static int run(String... args) {
    return SpringSeederCli.run(args, System.out, System.err);
  }

  @SpringBootConfiguration
  static class StartupApplication {
    static final AtomicInteger starts = new AtomicInteger();

    @Bean
    ApplicationRunner startup() {
      return args -> starts.incrementAndGet();
    }
  }
}
