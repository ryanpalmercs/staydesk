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

// Lets local dev apply/inspect migrations directly (./gradlew flywayMigrate, flywayInfo, etc.)
// without booting the whole app. Defaults match docker-compose.yml's local Postgres container;
// override via DATABASE_URL/DATABASE_USERNAME/DATABASE_PASSWORD to point at something else.
flyway {
    url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5433/staydesk_dev"
    user = System.getenv("DATABASE_USERNAME") ?: "postgres"
    password = System.getenv("DATABASE_PASSWORD") ?: "local_password"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}
