package io.github.brayandm.seeder.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.brayandm.seeder.SeedOptions;
import io.github.brayandm.seeder.SeederRunner;
import io.github.brayandm.seeder.spring.fixture.TestApplication;
import io.github.brayandm.seeder.spring.fixture.TestApplication.Orders;
import io.github.brayandm.seeder.spring.fixture.TestApplication.Repository;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

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
}
