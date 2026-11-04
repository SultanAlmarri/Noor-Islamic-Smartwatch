plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed"); showStandardStreams = true }
    // Research tools (accuracy fits against official timetables) run only on request: -Pnoor.research=true
    providers.gradleProperty("noor.research").orNull?.let { systemProperty("noor.research", it) }
}
