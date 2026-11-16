package io.github.brayandm.seeder.spring.fixture;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

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

  public static final class Repository {
    private int orders;

    public int orders() {
      return orders;
    }
  }

  @Seeder
  public static class Customers implements SeedTask<Long> {
    private final Repository repository;

    public Customers(Repository repository) {
      this.repository = repository;
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
