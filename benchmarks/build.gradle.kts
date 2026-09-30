plugins {
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.register<JavaExec>("benchmark") {
    group = "verification"
    description = "Compare long, BigInteger, and a long-first counter with JMH."
    dependsOn(tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }

    val resultFile = layout.buildDirectory.file("reports/jmh/results.json")
    doFirst {
        resultFile.get().asFile.parentFile.mkdirs()
    }

    args(
        providers.gradleProperty("benchFilter").orElse("com.silvia.apeiron.bench.NumberBenchmark").get(),
        "-wi", "3", "-i", "5", "-w", "1s", "-r", "1s", "-f", "2",
        "-bm", "avgt", "-tu", "ns", "-prof", "gc",
        "-rf", "json", "-rff", resultFile.get().asFile.absolutePath,
    )
}
