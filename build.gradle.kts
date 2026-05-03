plugins {
    java
    id("com.gradleup.shadow") version "9.0.0"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    implementation("net.dv8tion:JDA:5.2.1") {
        exclude(module = "opus-java")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
}

tasks.processResources {
    val tokens = mapOf("version" to project.version.toString())
    filesMatching("plugin.yml") {
        expand(tokens)
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    val shadedRoot = "buzz.chuz.motdbot.shaded"
    relocate("net.dv8tion.jda", "$shadedRoot.jda")
    relocate("com.neovisionaries", "$shadedRoot.neovisionaries")
    relocate("com.fasterxml.jackson", "$shadedRoot.jackson")
    relocate("okhttp3", "$shadedRoot.okhttp3")
    relocate("okio", "$shadedRoot.okio")
    relocate("org.slf4j", "$shadedRoot.slf4j")
    relocate("gnu.trove", "$shadedRoot.trove")
    relocate("org.apache.commons.collections4", "$shadedRoot.commonscollections4")
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
