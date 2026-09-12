plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.ktor.plugin")
}

group = "com.example.bank"
version = "0.1.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    // io.ktor.plugin's fatJar does NOT merge ServiceLoader descriptors by default —
    // Flyway's Plugin registry got clobbered by the postgresql module, leaving the
    // migration-prefix list empty ("Unrecognised migration name format"). Merging fixes it.
    mergeServiceFiles()
}

dependencies {
    val ktor = "2.3.12"
    implementation(platform("io.ktor:ktor-bom:$ktor"))
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-call-logging-jvm")
    implementation("io.ktor:ktor-server-call-id-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")

    // --- PostgreSQL persistence (Phase 2 target): Exposed + HikariCP + Flyway ---
    val exposed = "0.53.0"
    implementation("org.jetbrains.exposed:exposed-core:$exposed")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("org.flywaydb:flyway-core:10.20.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.20.1")

    // TODO(Phase 2): ktor-server-auth + auth-jwt for real user accounts
    implementation("ch.qos.logback:logback-classic:1.5.12")
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation(kotlin("test"))
}
