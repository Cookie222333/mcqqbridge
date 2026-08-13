plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.0"
}

group = "com.mcqq"
version = "1.0.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // Paper API（编译期，不打入 jar）
    compileOnly("io.papermc.paper:paper-api:26.2.build.11-alpha")

    // Java-WebSocket：自带独立线程，不依赖 JDK commonPool
    implementation("org.java-websocket:Java-WebSocket:1.5.6")
}

tasks {
    shadowJar {
        archiveClassifier.set("")
        archiveBaseName.set("mcqqbridge-paper")
        mergeServiceFiles()
        // 将 Java-WebSocket 打入 jar，避免依赖冲突
        relocate("org.java_websocket", "com.mcqq.bridge.shaded.java_websocket")
    }

    build {
        dependsOn(shadowJar)
    }

    processResources {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
