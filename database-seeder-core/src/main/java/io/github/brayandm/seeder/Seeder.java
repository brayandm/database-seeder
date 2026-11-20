package io.github.brayandm.seeder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the direct dependencies of a seed task. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Seeder {
  /**
   * Identifies the seeders that must complete before this one.
   *
   * @return the declared direct dependencies
   */
  Class<? extends SeedTask<?>>[] dependsOn() default {};
}
