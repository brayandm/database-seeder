# Database Seeder

Database Seeder is a Java 21 library for running data seeders in dependency order. A seeder can return a value, such as generated customer IDs, and another seeder can read that value through a typed context. The CLI can discover and run seeders without changes to your application's `main` method.

## Write your first seeders

Add these files to your application under `src/main/java/com/example/seeding/`. Automatic discovery requires a public, concrete class annotated with `@Seeder`, implementing `SeedTask<R>`, and exposing a public no-argument constructor. The classes below get that constructor implicitly.

**`CustomerSeeder.java`**

```java
package com.example.seeding;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.util.List;
import java.util.stream.LongStream;

@Seeder
public class CustomerSeeder implements SeedTask<List<Long>> {
    @Override
    public List<Long> run(SeedContext context) {
        return LongStream.rangeClosed(1, context.scaled(3)).boxed().toList();
    }
}
```

**`OrderSeeder.java`**

```java
package com.example.seeding;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import java.util.List;

@Seeder(dependsOn = CustomerSeeder.class)
public class OrderSeeder implements SeedTask<Void> {
    @Override
    public Void run(SeedContext context) {
        List<Long> customerIds = context.get(CustomerSeeder.class);
        System.out.println("Creating example orders for customers " + customerIds);
        return null;
    }
}
```

Running `OrderSeeder` runs `CustomerSeeder` first. With `--scale=2`, the customer seeder returns six IDs. The example uses in-memory values to show how results flow between seeders.

**Latest stable release:** `0.1.1` ([Maven Central](https://central.sonatype.com/artifact/io.github.brayandm/database-seeder-cli))

## Run with Gradle

In your application's `build.gradle.kts`, apply the Java plugin, add the dependency, and define the `seed` task. Merge these entries into any existing blocks.

```kotlin
plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.brayandm:database-seeder-cli:0.1.1")
}

tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Run Database Seeder."
    mainClass.set("io.github.brayandm.seeder.cli.SeederCli")
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    argumentProviders.add(CommandLineArgumentProvider { listOf("--discover") })
}
```

Run all discovered seeders or select one from the application directory:

```sh
./gradlew seed --args="--list"
./gradlew seed
./gradlew seed --args="--seeder=OrderSeeder --scale=2"
```

## Run with Maven

Add the CLI dependency to your application's `pom.xml`. Maven resolves the core
transitively.

```xml
<dependencies>
    <dependency>
        <groupId>io.github.brayandm</groupId>
        <artifactId>database-seeder-cli</artifactId>
        <version>0.1.1</version>
    </dependency>
</dependencies>
```

Use JDK 21 for Maven and add these properties to your application's POM.
Merge them into your existing `<properties>` block if you have one.

```xml
<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <seed.args/>
</properties>
```

Add the named execution under `<build><plugins>`. The POM supplies the
classpath, CLI entry point, and discovery flag. Pass your options through
`seed.args`.

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.7.0</version>
    <executions>
        <execution>
            <id>seed</id>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>${java.home}/bin/java</executable>
                <commandlineArgs>-cp "%classpath" io.github.brayandm.seeder.cli.SeederCli --discover ${seed.args}</commandlineArgs>
            </configuration>
        </execution>
    </executions>
</plugin>
```

From your application directory:

```sh
./mvnw compile exec:exec@seed
./mvnw compile exec:exec@seed -Dseed.args="--list"
./mvnw compile exec:exec@seed -Dseed.args="--seeder=OrderSeeder --scale=2"
```

Use `mvn` in place of `./mvnw` if you have Maven installed and your project
does not include the wrapper.

For a shorter command on Unix-like systems, save this as `seed` in the
application directory and run `chmod +x seed`. Pass the options as one
quoted string, just as with Gradle's `--args`.

```sh
#!/bin/sh
set -eu

if [ "$#" -gt 1 ]; then
    printf '%s\n' 'Usage: ./seed ["--seeder=OrderSeeder --scale=2"]' >&2
    exit 2
fi
cd "$(dirname "$0")"
if [ -x ./mvnw ]; then
    exec ./mvnw compile exec:exec@seed "-Dseed.args=${1:-}"
fi
exec mvn compile exec:exec@seed "-Dseed.args=${1:-}"
```

```sh
./seed
./seed "--list"
./seed "--seeder=OrderSeeder --scale=2"
```

## CLI options

| Option | Behavior |
| --- | --- |
| `--discover` | Finds eligible seeders in class directories and JARs on the runtime classpath. Both build examples enable discovery. |
| `--seeder=<class>` | Runs the selected seeder and its dependencies. Accepts a simple name when unique or a fully qualified class name. Omit it to run all available seeders. |
| `--only` | Runs just the selected seeder. Requires `--seeder`. Results from its dependencies are unavailable in this run. |
| `--linear` | Runs tasks sequentially. Eligible tasks run in parallel by default. |
| `--scale=<number>` | Sets a finite positive scale, defaulting to `1`. Seeders apply it through methods such as `context.scaled(3)`. |
| `--verbose` | Adds timestamps, durations, counters, fully qualified names, and execution settings to the live CLI output. |
| `--list` | Prints fully qualified seeder class names, one per line in alphabetical order, without running them. Use it without `--seeder` or `--only`. |
| `--help` | Prints usage and exits. |

By default, the CLI prints a live start and completion or failure line for each seeder. Any output written directly by your seeders also goes to the console.

For seeders that need constructor arguments, use a `SeederProvider` registered through Java's `ServiceLoader`, or pass initialized instances to `SeederCli.run(args, seeders)` from your own integration. Launch the standalone CLI without `--discover` to use registered providers.

## Run with Spring Boot

The starter was tested with Spring Boot 4.2.0 and Java 21.
It discovers concrete `@Seeder` classes implementing `SeedTask<R>` in the
application's Spring Boot auto-configuration packages and registers them as beans.
Spring can inject repositories or services through a seeder's constructor.

If you register an annotated seeder yourself with a Spring `@Bean` method,
declare the concrete seeder class as the return type. If the return type is
`SeedTask<R>`, name the bean after the seeder's fully qualified class name
(for example, `@Bean("com.example.seeding.CustomerSeeder")`). This lets the starter
skip automatic registration of the same seeder, including when Spring injects
a `List<SeedTask<?>>`.

```java
@Seeder(dependsOn = CustomerSeeder.class)
public class OrderSeeder implements SeedTask<Void> {
    private final OrderRepository repository;

    public OrderSeeder(OrderRepository repository) {
        this.repository = repository;
    }

    @Override
    public Void run(SeedContext context) {
        repository.createOrdersFor(context.get(CustomerSeeder.class));
        return null;
    }
}
```

### Gradle

Add this to your Spring Boot application's `build.gradle.kts`. Merge the
repository and dependency entries into your existing blocks. Set
`com.example.MyApplication` to the fully qualified name of your application's
`@SpringBootApplication` class.

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.brayandm:database-seeder-spring-boot-starter:0.1.1")
}

tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Run Database Seeder with Spring Boot."
    mainClass.set("io.github.brayandm.seeder.spring.SpringSeederCli")
    classpath = sourceSets.main.get().runtimeClasspath
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    argumentProviders.add(CommandLineArgumentProvider {
        listOf("--application=com.example.MyApplication")
    })
}
```

List available seeders or run a specific seeder.

```sh
./gradlew seed --args="--list"
./gradlew seed --args="--seeder=OrderSeeder --scale=2"
```

### Maven

Add these entries to your Spring Boot application's `pom.xml`. Merge them into
your existing blocks and set `com.example.MyApplication` to the fully qualified
name of your application's `@SpringBootApplication` class. Run Maven with JDK 21.

```xml
<properties>
    <seed.args/>
</properties>

<dependencies>
    <dependency>
        <groupId>io.github.brayandm</groupId>
        <artifactId>database-seeder-spring-boot-starter</artifactId>
        <version>0.1.1</version>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
            <version>3.7.0</version>
            <executions>
                <execution>
                    <id>seed</id>
                    <goals>
                        <goal>exec</goal>
                    </goals>
                    <configuration>
                        <executable>${java.home}/bin/java</executable>
                        <commandlineArgs>-cp "%classpath" io.github.brayandm.seeder.spring.SpringSeederCli --application=com.example.MyApplication ${seed.args}</commandlineArgs>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

```sh
./mvnw compile exec:exec@seed -Dseed.args="--list"
./mvnw compile exec:exec@seed -Dseed.args="--seeder=OrderSeeder --scale=2"
```

Use `mvn` if your project has no Maven wrapper. You can also use the `./seed`
script from the Maven section above.

For Spring option values containing spaces, quote the entire option inside the
argument string:

```sh
./gradlew seed --args='--seeder=OrderSeeder "--spring.application.name=Seed demo"'
./seed '--seeder=OrderSeeder "--spring.application.name=Seed demo"'
```

Use `--list` with the Spring launcher to see which seeders are available under
the active Spring profiles. Listing starts the Spring context to discover its
beans and skips seeder execution.

The command starts a Spring application context in console mode and closes it
when the CLI returns. Regular application startup registers the seeders and
exposes a `SeederRunner` bean for programmatic use. Execution starts when you
invoke the CLI or call the runner.


## Parallel runs and failures

A failed seeder blocks tasks that depend on it. Independent branches in a
parallel run can still complete. Set `--linear` to stop on the first failure in
execution order. Transaction boundaries and retry behavior are controlled by
your seeders and their repositories.

## Try a local development build

To try an unpublished version, run `./gradlew publishToMavenLocal` from a
Database Seeder checkout. In your application, replace the stable version in
the dependency with `developmentVersion` from the checkout's `gradle.properties`.
If your application uses Gradle, add `mavenLocal()` before `mavenCentral()` in
its repositories block.
