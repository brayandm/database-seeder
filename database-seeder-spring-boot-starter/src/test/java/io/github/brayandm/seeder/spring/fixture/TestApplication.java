package io.github.brayandm.seeder.spring.fixture;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.util.List;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {
  @Bean
  Repository repository() {
    return new Repository();
  }

  @Bean
  static BeanPostProcessor proxyCustomers() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String name) {
        return bean instanceof Customers ? new ProxyFactory(bean).getProxy() : bean;
      }
    };
  }

  @Bean
  @Profile("explicit-seeder")
  SeedTask<Long> explicitCustomers(Repository repository) {
    return new Customers(repository);
  }

  @Bean(name = "io.github.brayandm.seeder.spring.fixture.TestApplication$Customers")
  @Profile("named-seeder")
  SeedTask<Long> namedCustomers(Repository repository) {
    return new Customers(repository);
  }

  @Bean
  @Profile("named-seeder")
  InjectedSeeders injectedSeeders(List<SeedTask<?>> seeders) {
    return new InjectedSeeders(seeders);
  }

  public record InjectedSeeders(List<SeedTask<?>> seeders) {}

  @Bean
  @Profile("console-test")
  ApplicationRunner checkConsoleMode(Environment environment) {
    return args -> {
      if (!"none".equals(environment.getProperty("spring.main.web-application-type"))) {
        throw new IllegalStateException("Expected console mode");
      }
    };
  }

  @Seeder
  @Profile("seed-test")
  public static class ProfileSeeder implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
      return null;
    }
  }

  public static final class Repository {
    private int orders;
    private int customerInstances;

    public int customerInstances() {
      return customerInstances;
    }

    public int orders() {
      return orders;
    }
  }

  @Seeder
  public static class Customers implements SeedTask<Long> {
    private final Repository repository;

    public Customers(Repository repository) {
      this.repository = repository;
      repository.customerInstances++;
    }

    @Override
    public Long run(SeedContext context) {
      return context.scaled(3) + repository.orders();
    }
  }

  @Seeder(dependsOn = Customers.class)
  public static class Orders implements SeedTask<Void> {
    private final Repository repository;

    public Orders(Repository repository) {
      this.repository = repository;
    }

    @Override
    public Void run(SeedContext context) {
      repository.orders = Math.toIntExact(context.get(Customers.class));
      return null;
    }
  }
}
