plugins {
	java
	application
}

group = "com.staydesk"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.java-websocket:Java-WebSocket:1.5.4")
	implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
	mainClass = "com.staydesk.mockterminal.MockTerminalMain"
}