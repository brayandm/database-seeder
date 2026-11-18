package io.github.brayandm.seeder.spring.jpa;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.List;
import java.util.stream.LongStream;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@SpringBootApplication
public class JpaTestApplication {
  @Entity
  public static class Customer {
    @Id private Long id;

    protected Customer() {}

    Customer(long id) {
      this.id = id;
    }
  }

  @Seeder
  static class Customers implements SeedTask<List<Long>> {
    private final CustomerRepository repository;

    Customers(CustomerRepository repository) {
      this.repository = repository;
    }

    @Override
    @Transactional
    public List<Long> run(SeedContext context) {
      var ids = LongStream.rangeClosed(1, context.scaled(3)).boxed().toList();
      repository.saveAll(ids.stream().map(Customer::new).toList());
      return ids;
    }
  }

  @Seeder(dependsOn = Customers.class)
  static class Orders implements SeedTask<Void> {
    private final CustomerRepository repository;

    Orders(CustomerRepository repository) {
      this.repository = repository;
    }

    @Override
    public Void run(SeedContext context) {
      if (context.get(Customers.class).size() != 6 || repository.count() != 6) {
        throw new IllegalStateException("Expected six committed customers");
      }
      return null;
    }
  }
}

interface CustomerRepository extends JpaRepository<JpaTestApplication.Customer, Long> {}
