
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

minecraft {
    extraRunJvmArguments.addAll("-Xms1G", "-Xmx8G")
}

apply(from = "gradle/wireless-benchmark.gradle")

// Include the project license, notices and license texts for ported or adapted code.
tasks.withType<Jar>().configureEach {
    from("LICENSE") { into("META-INF") }
    from("licenses") { into("META-INF/licenses") }
    from("THIRD_PARTY_NOTICES.md") { into("META-INF") }
}
