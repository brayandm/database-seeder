"""Run the README example against locally published artifacts from Gradle and Maven."""

import os
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLI = "io.github.brayandm.seeder.cli.SeederCli"
VERSION = "0.1.0-SNAPSHOT"
DEPENDENCY = f"io.github.brayandm:database-seeder-cli:{VERSION}"


def check_output(command, cwd):
    result = subprocess.run(command, cwd=cwd, text=True, stdout=subprocess.PIPE)
    print(result.stdout, end="")
    result.check_returncode()
    if "Creating example orders for customers [1, 2, 3, 4, 5, 6]" not in result.stdout:
        raise AssertionError("The published CLI did not run the example with scale 2")


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
        check_output(
            [
                os.environ.get("MAVEN", "mvn"), "-B", *repository_args, "compile",
                "org.codehaus.mojo:exec-maven-plugin:3.7.0:exec",
                "-Dexec.executable=java",
                f"-Dexec.args=-cp %classpath {CLI} --discover --seeder=OrderSeeder --scale=2",
            ],
            app,
        )


if __name__ == "__main__":
    main()
