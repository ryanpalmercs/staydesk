import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.ExecOperations

buildscript {
    dependencies {
        // Flyway Gradle plugin needs the JDBC driver and the Postgres database plugin on its own
        // classpath, separate from the project's runtime dependencies below.
        classpath("org.postgresql:postgresql:42.7.5")
        classpath("org.flywaydb:flyway-database-postgresql:13.6.0")
    }
}

plugins {
    java
    groovy
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "13.6.0"
}

group = "com.staydesk"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.postgresql:postgresql")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("com.openhtmltopdf:openhtmltopdf-pdfbox:1.0.10")
    implementation("com.twilio.sdk:twilio:10.6.2")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    implementation("net.authorize:anet-java-sdk:3.0.0")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.spockframework:spock-core:2.3-groovy-4.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Local dev DB connection: defaults match docker-compose.yml's Postgres container, overridable
// via DATABASE_URL/DATABASE_USERNAME/DATABASE_PASSWORD the same way the app itself is configured.
val repoRoot = rootDir.parentFile
val localDbUrl = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/staydesk_dev"
val localDbUser = System.getenv("DATABASE_USERNAME") ?: "postgres"
val localDbPassword = System.getenv("DATABASE_PASSWORD") ?: "local_password"

// Lets local dev apply/inspect migrations directly (./gradlew flywayMigrate, flywayInfo, etc.)
// without booting the whole app.
flyway {
    url = localDbUrl
    user = localDbUser
    password = localDbPassword
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}

// One-command local dev DB reset: cycles the docker-compose Postgres container to a completely
// empty state, applies every migration, and reseeds reference data. Chained via dependsOn so
// each step only runs once the previous one has genuinely finished — in particular, dbWaitReady
// blocks flywayMigrate until the fresh container is actually accepting connections, since
// `docker compose up -d` returns long before postgres finishes initializing.

tasks.register<Exec>("dbDown") {
    group = "local dev"
    description = "Stops and removes the local Postgres container and its data volume."
    workingDir = repoRoot
    commandLine("docker", "compose", "down", "--volumes")
}

tasks.register<Exec>("dbUp") {
    group = "local dev"
    description = "Starts a fresh local Postgres container."
    workingDir = repoRoot
    commandLine("docker", "compose", "up", "-d")
    dependsOn("dbDown")
}

tasks.register("dbWaitReady") {
    group = "local dev"
    description = "Blocks until the local Postgres container is accepting connections."
    dependsOn("dbUp")
    doLast {
        val execOps = serviceOf<ExecOperations>()
        val maxAttempts = 30
        var ready = false
        for (attempt in 1..maxAttempts) {
            val result = execOps.exec {
                workingDir = repoRoot
                commandLine("docker", "exec", "staydesk_dev", "pg_isready", "-U", localDbUser, "-d", "staydesk_dev")
                isIgnoreExitValue = true
            }
            if (result.exitValue == 0) {
                ready = true
                break
            }
            Thread.sleep(1000)
        }
        if (!ready) {
            throw GradleException("Postgres in staydesk_dev didn't become ready within $maxAttempts seconds")
        }
    }
}

tasks.named("flywayMigrate") {
    dependsOn("dbWaitReady")
}

tasks.register<Exec>("dbSeed") {
    group = "local dev"
    description = "Seeds room_types/rooms/rates reference data into the local dev DB."
    workingDir = repoRoot
    environment("PGPASSWORD", localDbPassword)
    // Unlike flywayMigrate, this always targets docker-compose.yml's local container directly
    // (psql takes discrete host/port flags, not a JDBC URL) — DATABASE_URL doesn't affect it.
    commandLine(
        "psql", "-h", "localhost", "-p", "5433", "-U", localDbUser,
        "-d", "staydesk_dev", "-f", "backend/scripts/seedlocaldevdata.sql"
    )
    dependsOn("flywayMigrate")
}

tasks.register("resetLocalDb") {
    group = "local dev"
    description = "Full local dev reset: cycles docker, applies all migrations, reseeds reference data."
    dependsOn("dbSeed")
}
