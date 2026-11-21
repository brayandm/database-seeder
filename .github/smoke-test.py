"""Run external Gradle and Maven projects against the locally published artifacts."""

import os
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLI = "io.github.brayandm.seeder.cli.SeederCli"
VERSION = "0.1.0-SNAPSHOT"
DEPENDENCY = f"io.github.brayandm:database-seeder-cli:{VERSION}"
EXPECTED_ORDERS = "Creating example orders for customers [1, 2, 3, 4, 5, 6]"


def check_output(command, cwd, expected=EXPECTED_ORDERS):
    result = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE)
    print(result.stdout, end="")
    result.check_returncode()
    if expected not in result.stdout:
        raise AssertionError(f"Missing expected output: {expected}")
    return result.stdout


def main():
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    local_repository = os.environ.get("MAVEN_LOCAL_REPO")
    repository_args = [f"-Dmaven.repo.local={local_repository}"] if local_repository else []
    classes = re.findall(r"```java\n(.*?)```", readme, re.DOTALL)[:2]

    with tempfile.TemporaryDirectory(prefix="database-seeder-smoke-") as directory:
        app = Path(directory)
        sources = app / "src/main/java/com/example/seeding"
        sources.mkdir(parents=True)
        for name, source in zip(("CustomerSeeder", "OrderSeeder"), classes, strict=True):
            (sources / f"{name}.java").write_text(source, encoding="utf-8")

        (app / "settings.gradle.kts").write_text('rootProject.name = "consumer"\n')
        (app / "build.gradle.kts").write_text(f"""
plugins {{ java }}
repositories {{ mavenLocal(); mavenCentral() }}
dependencies {{ implementation("{DEPENDENCY}") }}
tasks.register<JavaExec>("seed") {{
    mainClass.set("{CLI}")
    classpath = sourceSets.main.get().runtimeClasspath
    argumentProviders.add(CommandLineArgumentProvider {{ listOf("--discover") }})
}}
""")
        check_output(
            [str(ROOT / "gradlew"), "--no-daemon", *repository_args, "seed",
             "--args=--seeder=OrderSeeder --scale=2"],
            app,
        )

        (app / "pom.xml").write_text(f"""
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId><artifactId>consumer</artifactId><version>1.0</version>
  <properties><maven.compiler.release>21</maven.compiler.release></properties>
  <dependencies><dependency>
    <groupId>io.github.brayandm</groupId><artifactId>database-seeder-cli</artifactId>
    <version>{VERSION}</version>
  </dependency></dependencies>
</project>
""")
        check_output(maven_command(repository_args, CLI,
                                   "--discover --seeder=OrderSeeder --scale=2"), app)
        check_spring_starter(app / "spring", repository_args)


def maven_command(repository_args, main_class, arguments):
    return [
        os.environ.get("MAVEN", "mvn"), "-B", *repository_args, "compile",
        "org.codehaus.mojo:exec-maven-plugin:3.7.0:exec",
        "-Dexec.executable=java",
        f"-Dexec.args=-cp %classpath {main_class} {arguments}",
    ]


def check_spring_starter(app, repository_args):
    sources = app / "src/main/java/com/example/spring"
    sources.mkdir(parents=True)
    (sources / "Application.java").write_text("""
package com.example.spring;

import io.github.brayandm.seeder.SeedContext;
import io.github.brayandm.seeder.SeedTask;
import io.github.brayandm.seeder.Seeder;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Application {
    @Bean
    Message message() {
        return new Message("Injected into the published starter");
    }
}

record Message(String value) {}

@Seeder
class SmokeSeeder implements SeedTask<Void> {
    private final Message message;

    SmokeSeeder(Message message) {
        this.message = message;
    }

    public Void run(SeedContext context) {
        System.out.println(message.value());
        return null;
    }
}
""", encoding="utf-8")
    (app / "pom.xml").write_text(f"""
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId><artifactId>spring-consumer</artifactId><version>1.0</version>
  <properties><maven.compiler.release>21</maven.compiler.release></properties>
  <dependencies>
    <dependency>
      <groupId>io.github.brayandm</groupId>
      <artifactId>database-seeder-spring-boot-starter</artifactId>
      <version>{VERSION}</version>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webmvc</artifactId>
      <version>4.2.0</version>
    </dependency>
  </dependencies>
</project>
""", encoding="utf-8")

    output = check_output(
        maven_command(
            repository_args,
            "io.github.brayandm.seeder.spring.SpringSeederCli",
            "--application=com.example.spring.Application --seeder=SmokeSeeder"
            " --spring.main.web-application-type=servlet",
        ),
        app,
        "Injected into the published starter",
    )
    if "Tomcat initialized" in output or "Tomcat started" in output:
        raise AssertionError("The Spring seeding CLI started a web server")


if __name__ == "__main__":
    main()
