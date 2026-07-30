import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
}

val releaseVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.environmentVariable("RELEASE_VERSION"))
val paperApiVersion = providers.gradleProperty("paperApiVersion")
    .orElse("1.20.1-R0.1-SNAPSHOT")
val targetJavaVersion = providers.gradleProperty("targetJavaVersion")
    .map(String::toInt)
    .orElse(17)

group = "io.github.notzorba"
version = releaseVersion.getOrElse("dev-SNAPSHOT")

val bundled by configurations.creating

configurations.compileOnly {
    extendsFrom(bundled)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    // Compile against the oldest supported Paper API so newer-only calls cannot
    // accidentally enter the release jar. Paper keeps this API compatible on
    // newer servers, including the year-based 26.x releases.
    compileOnly("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.7")

    bundled("org.xerial:sqlite-jdbc:3.50.3.0")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("io.papermc.paper:paper-api:${paperApiVersion.get()}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion.get()))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion.get())
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from({
        bundled
            .filter { it.exists() }
            .map { dependency -> if (dependency.isDirectory) dependency else zipTree(dependency) }
    })

    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
    }
}
