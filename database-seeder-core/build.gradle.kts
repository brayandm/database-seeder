plugins {
    `java-library`
    checkstyle
    id("com.diffplug.spotless") version "8.4.0"
}


repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:6.0.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
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
