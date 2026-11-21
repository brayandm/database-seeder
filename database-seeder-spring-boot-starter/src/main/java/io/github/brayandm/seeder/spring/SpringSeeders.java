package io.github.brayandm.seeder.spring;

import io.github.brayandm.seeder.SeedTask;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

final class SpringSeeders {
  static final String DISCOVERED = SpringSeeders.class.getName() + ".discovered";

  private SpringSeeders() {}

  static List<SeedTask<?>> collect(ConfigurableListableBeanFactory beans) {
    var seeders = new ArrayList<SeedTask<?>>();
    var explicitTypes = new HashSet<String>();
    var discoveredNames = new ArrayList<String>();

    for (var name : beans.getBeanNamesForType(SeedTask.class)) {
      if (beans.containsBeanDefinition(name)
          && Boolean.TRUE.equals(beans.getBeanDefinition(name).getAttribute(DISCOVERED))) {
        discoveredNames.add(name);
      } else {
        SeedTask<?> seeder = beans.getBean(name, SeedTask.class);
        seeders.add(seeder);
        explicitTypes.add(AopProxyUtils.ultimateTargetClass(seeder).getName());
      }
    }

    for (var name : discoveredNames) {
      if (!explicitTypes.contains(beans.getBeanDefinition(name).getBeanClassName())) {
        seeders.add(beans.getBean(name, SeedTask.class));
      }
    }
    return seeders;
  }
}
