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
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.google.code.gson:gson:2.10.1")
    testImplementation("io.papermc.paper:paper-api:1.20-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(26)
}

tasks.compileJava {
    options.release.set(17)
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
}

// Opt-in private evaluation. Normal build/test never reads a real archive or calls a model.
tasks.register<JavaExec>("evaluateLore") {
    group = "verification"
    description = "Evaluate production lore code against explicitly supplied private data/cases (no AI)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.l299l.npcspeak.knowledge.LoreEvaluation")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(26)) })
    args("--mode", providers.gradleProperty("loreMode").getOrElse("run"),
         "--data", providers.gradleProperty("loreData").getOrElse(""),
         "--cases", providers.gradleProperty("loreCases").getOrElse(""),
         "--output", providers.gradleProperty("loreOutput").getOrElse(""))
}

tasks.register<JavaExec>("prepareLorePilot") {
    group = "verification"
    description = "Export private pilot requests through production prompt/serialization code; no HTTP."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.l299l.npcspeak.knowledge.LorePilotPreparation")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(26)) })
    args("--data", providers.gradleProperty("loreData").getOrElse(""),
         "--cases", providers.gradleProperty("pilotCases").getOrElse(""),
         "--style", providers.gradleProperty("pilotStyle").getOrElse(""),
         "--npc", providers.gradleProperty("pilotNpc").getOrElse(""),
         "--model", providers.gradleProperty("pilotModel").getOrElse(""),
         "--output", providers.gradleProperty("loreOutput").getOrElse(""))
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
