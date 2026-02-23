plugins {
    kotlin("jvm") version "1.8.20"
}

group = "com.github.beigirad"
version = "1.6"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
    testImplementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.platform:junit-platform-testkit:1.10.1")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("baseline.output", projectDir.resolve("sample-baseline").absolutePath)
    systemProperty("baseline.root", projectDir.absolutePath)
    systemProperty("baseline.record", System.getProperty("baseline.record", "false"))
}
kotlin {
    jvmToolchain(17)
}