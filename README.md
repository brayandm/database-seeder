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

## Run with Gradle

The current version is `0.1.0-SNAPSHOT`. To use it from source, keep a checkout of this repository beside your application and add the following to your application's `settings.gradle.kts`:

```kotlin
includeBuild("../database-seeder")
```

In your application's `build.gradle.kts`, apply the Java plugin, add the dependency, and define the `seed` task. Merge these entries into any existing blocks.

```kotlin
plugins {
    java
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    implementation("io.github.brayandm:database-seeder-cli:0.1.0-SNAPSHOT")
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
./gradlew seed
./gradlew seed --args="--seeder=OrderSeeder --scale=2"
```

## Run with Maven

Install the snapshot and its publication metadata into your local Maven repository.
Run this command from the root of the Database Seeder checkout:

```sh
./gradlew publishToMavenLocal
```

Add the CLI dependency to your application's `pom.xml`. Maven resolves the core
transitively.

```xml
<dependencies>
    <dependency>
        <groupId>io.github.brayandm</groupId>
        <artifactId>database-seeder-cli</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

Use JDK 21 for Maven and set the compiler release to `21` in your application. Add this execution under `<build><plugins>`:

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
                <arguments>
                    <argument>-cp</argument>
                    <classpath/>
                    <argument>io.github.brayandm.seeder.cli.SeederCli</argument>
                    <argument>--discover</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

From your application's directory, run the named execution:

```sh
./mvnw compile exec:exec@seed
```

To pass custom options, use `-Dexec.args`. It replaces the configured argument list, so include the classpath, entry point, and discovery flag:

```sh
./mvnw compile exec:exec@seed \
  -Dexec.args='-cp "%classpath" io.github.brayandm.seeder.cli.SeederCli --discover --seeder=OrderSeeder --scale=2'
```

If your application uses an installed Maven distribution, replace `./mvnw` with `mvn`.

## CLI options

| Option | Behavior |
| --- | --- |
| `--discover` | Instantiates eligible seeders from class directories and JARs on the runtime classpath. Both build examples enable discovery. |
| `--seeder=<class>` | Runs the selected seeder and its dependencies. Accepts a simple name when unique or a fully qualified class name. Omit it to run all available seeders. |
| `--only` | Runs just the selected seeder. Requires `--seeder`. Results from its dependencies are unavailable in this run. |
| `--linear` | Runs tasks sequentially. Eligible tasks run in parallel by default. |
| `--scale=<number>` | Sets a finite positive scale, defaulting to `1`. Seeders apply it through methods such as `context.scaled(3)`. |
| `--verbose` | Adds timestamps, durations, counters, fully qualified names, and execution settings to the live CLI output. |
| `--help` | Prints usage and exits. |

By default, the CLI prints a live start and completion or failure line for each seeder. Any output written directly by your seeders also goes to the console.

For seeders that need constructor arguments, use a `SeederProvider` registered through Java's `ServiceLoader`, or pass initialized instances to `SeederCli.run(args, seeders)` from your own integration. Launch the standalone CLI without `--discover` to use registered providers.

## Run with Spring Boot

Use `io.github.brayandm:database-seeder-spring-boot-starter:0.1.0-SNAPSHOT` in
place of the CLI dependency. The starter was tested with Spring Boot 4.2.0 and Java 21.
It discovers concrete `@Seeder` classes implementing `SeedTask<R>` in the
application's Spring Boot auto-configuration packages and registers them as beans.
Spring can inject repositories or services through a seeder's constructor.

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

For Gradle, use the same `seed` task shown above with
`io.github.brayandm.seeder.spring.SpringSeederCli` as the main class. Replace
`--discover` in the argument provider with `--application=com.example.MyApplication`,
using the fully qualified name of your Spring Boot configuration class. Then run:

```sh
./gradlew seed --args="--seeder=OrderSeeder --scale=2"
```

For Maven, run the same `publishToMavenLocal` command and replace the CLI
dependency with the starter dependency. In the named execution,
replace the CLI main class and `--discover` with these arguments:

```xml
<argument>io.github.brayandm.seeder.spring.SpringSeederCli</argument>
<argument>--application=com.example.MyApplication</argument>
```

The command starts a Spring application context in console mode and closes it
when the CLI returns. Regular application startup registers the seeders and
exposes a `SeederRunner` bean for programmatic use. Execution starts when you
invoke the CLI or call the runner.


## Parallel runs and failures

A failed seeder blocks tasks that depend on it. Independent branches in a
parallel run can still complete. Set `--linear` to stop on the first failure in
execution order. Transaction boundaries and retry behavior are controlled by
your seeders and their repositories.

## Prepare a release

Run `./gradlew check publishToMavenLocal` and `python3 .github/smoke-test.py` with
Java 21 and Maven installed to verify the published metadata from both build
tools. For a signed release, export your ASCII-armored PGP secret key as
`DATABASE_SEEDER_SIGNING_KEY` and its passphrase as
`DATABASE_SEEDER_SIGNING_PASSWORD` if applicable. Then build the bundle:

```sh
./gradlew check centralBundle -PreleaseVersion=0.1.0
```

The bundle is written to `build/distributions/database-seeder-0.1.0.zip`.
It can be uploaded manually in [Sonatype Central Portal](https://central.sonatype.com/)
after verifying the `io.github.brayandm` namespace.
