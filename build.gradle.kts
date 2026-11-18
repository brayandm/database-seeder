import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

val publicationVersion = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT")
val signingKey = providers.environmentVariable("DATABASE_SEEDER_SIGNING_KEY")
val signingPassword = providers.environmentVariable("DATABASE_SEEDER_SIGNING_PASSWORD")
val releaseRepository = layout.buildDirectory.dir("release-repository")

subprojects {
    group = "io.github.brayandm"
    version = publicationVersion.get()

    plugins.withId("java-library") {
        pluginManager.apply("maven-publish")
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }

        tasks.named<Jar>("jar") {
            from(rootProject.file("LICENSE")) {
                into("META-INF")
            }
        }

        val publication = extensions.getByType<PublishingExtension>().run {
            repositories {
                maven {
                    name = "ReleaseBundle"
                    url = releaseRepository.get().asFile.toURI()
                }
            }
            publications.create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set("Dependency-aware data seeding for Java applications.")
                    url.set("https://github.com/brayandm/database-seeder")
                    licenses {
                        license {
                            name.set("Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    developers {
                        developer {
                            id.set("brayandm")
                            name.set("brayandm")
                            url.set("https://github.com/brayandm")
                        }
                    }
                    scm {
                        connection.set("scm:git:https://github.com/brayandm/database-seeder.git")
                        developerConnection.set("scm:git:ssh://git@github.com/brayandm/database-seeder.git")
                        url.set("https://github.com/brayandm/database-seeder")
                    }
                }
            }
        }

        if (signingKey.isPresent) {
            pluginManager.apply("signing")
            extensions.configure<SigningExtension> {
                useInMemoryPgpKeys(signingKey.get(), signingPassword.orElse("").get())
                sign(publication)
            }
        }
    }
}

tasks.register<Zip>("centralBundle") {
    group = "publishing"
    description = "Builds a signed release bundle for manual upload to Maven Central."
    dependsOn(subprojects.map { "${it.path}:publishMavenJavaPublicationToReleaseBundleRepository" })
    val release = publicationVersion.get()
    from(releaseRepository) {
        include("io/github/brayandm/*/$release/**")
        exclude("**/*.module*", "**/*.asc.*")
    }
    archiveFileName.set("database-seeder-$release.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    doFirst {
        require(!release.endsWith("-SNAPSHOT")) { "Set -PreleaseVersion to a release version." }
        require(signingKey.isPresent) { "Set DATABASE_SEEDER_SIGNING_KEY to sign the release." }
    }
}
