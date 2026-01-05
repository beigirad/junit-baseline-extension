plugins {
    kotlin("jvm") version "2.1.21"
}

group = "ir.beigirad"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.moshi:moshi:1.15.2")
    implementation("io.kotest:kotest-runner-junit5:5.9.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.platform:junit-platform-testkit:1.10.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}