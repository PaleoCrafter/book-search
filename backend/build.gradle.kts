import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.3.40"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

group = "io.paleocrafter"
version = "0.1.0"

repositories {
    mavenCentral()
    jcenter()
    maven {
        url = uri("https://github.com/psiegman/mvn-repo/raw/master/releases")
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("io.ktor:ktor-server-core:1.2.1")
    implementation("io.ktor:ktor-server-netty:1.2.1")
    implementation("io.ktor:ktor-jackson:1.2.1")
    implementation("io.ktor:ktor-auth:1.2.1")
    implementation("io.ktor:ktor-server-sessions:1.2.1")
    implementation("org.postgresql:postgresql:42.2.5")
    implementation("com.zaxxer:HikariCP:2.7.8")
    implementation("org.jetbrains.exposed:exposed:0.15.1")
    implementation("org.elasticsearch.client:elasticsearch-rest-high-level-client:7.1.1")
    implementation("nl.siegmann.epublib:epublib-core:3.1")
    implementation("org.jsoup:jsoup:1.12.1")
    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.11.2")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs = listOf(
        "-Xnew-inference",
        "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi",
        "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi",
        "-Xuse-experimental=io.ktor.util.KtorExperimentalAPI"
    )
}

application {
    mainClassName = "io.ktor.server.netty.EngineMain"
}

tasks.withType<Jar> {
    archiveFileName.set("book-search.jar")
    manifest {
        attributes(
            mapOf(
                "Main-Class" to application.mainClassName
            )
        )
    }
}
