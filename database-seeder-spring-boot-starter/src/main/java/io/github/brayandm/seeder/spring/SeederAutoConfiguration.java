package io.github.brayandm.seeder.spring;

import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import io.github.brayandm.seeder.SeederRunner;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.FullyQualifiedAnnotationBeanNameGenerator;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** Registers annotated seeders and provides a runner for Spring Boot applications. */
@AutoConfiguration
public class SeederAutoConfiguration {
  /** Creates the auto-configuration instance for Spring Boot. */
  public SeederAutoConfiguration() {}

  @Bean
  static BeanDefinitionRegistryPostProcessor seederRegistration(Environment environment) {
    return registry -> {
      if (!(registry instanceof BeanFactory factory) || !AutoConfigurationPackages.has(factory)) {
        return;
      }

      var scanner =
          new ClassPathBeanDefinitionScanner(registry, false, environment) {
            @Override
            protected void registerBeanDefinition(
                BeanDefinitionHolder holder, BeanDefinitionRegistry registry) {
              var definition = holder.getBeanDefinition();
              definition.setAttribute(SpringSeeders.DISCOVERED, true);
              definition.setLazyInit(true);
              super.registerBeanDefinition(holder, registry);
            }

            @Override
            protected boolean checkCandidate(String beanName, BeanDefinition candidate) {
              for (var registeredName : registry.getBeanDefinitionNames()) {
                var registeredType = factory.getType(registeredName, false);
                if (registeredType != null
                    && SeedTask.class.isAssignableFrom(registeredType)
                    && (beanName.equals(registeredName)
                        || registeredType.getName().equals(candidate.getBeanClassName()))) {
                  return false;
                }
              }
              return super.checkCandidate(beanName, candidate);
            }
          };
      scanner.setBeanNameGenerator(FullyQualifiedAnnotationBeanNameGenerator.INSTANCE);
      var annotation = new AnnotationTypeFilter(Seeder.class);
      var task = new AssignableTypeFilter(SeedTask.class);
      scanner.addIncludeFilter(
          (reader, factoryReader) ->
              annotation.match(reader, factoryReader) && task.match(reader, factoryReader));
      scanner.scan(AutoConfigurationPackages.get(factory).toArray(String[]::new));
    };
  }

  @Bean
  @ConditionalOnMissingBean
  SeederRunner seederRunner(ConfigurableListableBeanFactory beans) {
    return new SeederRunner(
        SpringSeeders.collect(beans), event -> {}, AopProxyUtils::ultimateTargetClass);
  }
}
