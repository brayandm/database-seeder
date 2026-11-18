plugins {
    `java-library`
    checkstyle
    id("com.diffplug.spotless") version "8.4.0"
}


repositories {
    mavenCentral()
}

dependencies {
    api(project(":database-seeder-cli"))
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.2.0"))
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-aop")

    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

spotless {
    java {
        googleJavaFormat("1.25.2")
    }
}

checkstyle {
    toolVersion = "10.26.1"
    configDirectory.set(rootProject.layout.projectDirectory.dir("config/checkstyle"))
}
