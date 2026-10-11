plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.finallyapp"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-webclient")
	runtimeOnly("org.xerial:sqlite-jdbc")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
	testImplementation("com.squareup.okhttp3:mockwebserver3:5.5.0")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val testDb = layout.buildDirectory.dir("test-db")

tasks.withType<Test> {
	useJUnitPlatform()
	doFirst { testDb.get().asFile.deleteRecursively() }
}

tasks.bootJar {
	archiveFileName = "finally.jar"
}
