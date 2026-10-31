plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.citizensnpcs.co/")
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("net.citizensnpcs:citizensapi:2.0.33-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(26)
}

tasks.compileJava {
    options.release.set(17)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.20")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        // Local backup files and the private wiki must never enter a published JAR.
        exclude("**/*.bak", "knowledge/wiki/**")
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
